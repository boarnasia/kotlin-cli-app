package io.github.boarnasia.cli.seed

import io.github.boarnasia.cli.CliFailure
import io.github.boarnasia.cli.ErrorCode
import io.github.boarnasia.core.account.Email
import io.github.boarnasia.core.account.PageRange
import io.github.boarnasia.core.db.Actor
import io.github.boarnasia.core.db.CoreDb
import io.github.boarnasia.core.db.CoreException
import io.github.boarnasia.core.db.id.DisplayNumber
import io.github.boarnasia.core.db.id.DisplayNumbers
import io.github.boarnasia.core.db.id.EntityRef
import io.github.boarnasia.core.db.id.IdGenerator
import io.github.boarnasia.core.item.ItemQuery
import io.github.boarnasia.core.item.ItemService
import io.github.boarnasia.core.item.StockService
import io.github.boarnasia.core.notice.NoticeService
import io.github.boarnasia.core.order.OrderLineRequest
import io.github.boarnasia.core.order.OrderService
import io.github.boarnasia.core.password.Argon2idPasswordHasher
import io.github.boarnasia.core.staff.Permission
import io.github.boarnasia.core.staff.StaffService
import io.github.boarnasia.core.user.Registrant
import io.github.boarnasia.core.user.SqliteUserRepository
import io.github.boarnasia.core.user.UserService
import java.time.Clock
import java.util.Locale
import java.util.UUID

data class SeedCreated(val id: Long, val displayNumber: DisplayNumber, val publicId: UUID? = null)

data class SeedModuleResult(val module: SeedModule, val created: List<SeedCreated>)

/** 注文が参照する会員（正規化したメールアドレス）と商品（名前）の内部 ID。 */
data class OrderRefs(val members: Map<String, Long>, val items: Map<String, Long>)

const val INITIAL_STOCK_REASON = "初期在庫"

private fun normalizeEmail(raw: String) = raw.trim().lowercase(Locale.ROOT)

/**
 * 定義を core のユースケースで 1 件ずつ投入する。ユースケースは 1 件ずつ別のトランザクションなので、
 * 途中で失敗したら書いた分は残る。その位置と、書いた分が残ることをエラーに添える。
 */
class Seeder(private val db: CoreDb, ids: IdGenerator, private val clock: Clock = Clock.systemUTC()) {
    private val hasher = Argon2idPasswordHasher()
    private val staffs = StaffService(db, ids, hasher, clock)
    private val users = UserService(db, ids, hasher, clock)
    private val items = ItemService(db, ids, clock)
    private val stock = StockService(db, ids, clock)
    private val orders = OrderService(db, ids, clock)
    private val notices = NoticeService(db, ids, clock)
    private var written = 0

    /** 管理者がいる・会員がいるなどを、書き始める前に確かめる。 */
    fun requireEmpty(modules: Collection<SeedModule>) {
        val filled = modules.filter { DisplayNumbers.lastIssued(db, it.kind) > 0 }
        if (filled.isNotEmpty()) {
            throw CliFailure(
                ErrorCode.CONFLICT,
                "既にデータがあるので投入しません: ${filled.joinToString("・") { it.key }}（db reset --yes で作り直してから実行してください）",
            )
        }
    }

    /** 作った最初の管理者を返す。残りの管理者と、seed all の以降のモジュールはこの管理者が操作する。 */
    fun staff(file: StaffSeedFile): Pair<Actor.Staff, SeedModuleResult> {
        val first = at("staff.json の bootstrap") { write { staffs.bootstrap(file.bootstrap.email, file.bootstrap.name, file.bootstrap.password) } }
        val actor = Actor.Staff(first.id)
        val rest = file.staff.mapIndexed { i, s ->
            at("staff.json の staff の ${i + 1} 件目") {
                val permissions = s.permissions.map { Permission.ofCode(it)!! }.toSet()
                val staff = write { staffs.register(s.email, s.name, s.password, permissions, actor) }
                if (s.status == SeedAccountStatus.SUSPENDED) write { staffs.suspend(EntityRef.Internal(staff.id), s.suspendReason, actor) }
                SeedCreated(staff.id, staff.displayNumber)
            }
        }
        return actor to SeedModuleResult(SeedModule.STAFF, listOf(SeedCreated(first.id, first.displayNumber)) + rest)
    }

    fun users(file: UserSeedFile, actor: Actor.Staff): Pair<SeedModuleResult, Map<String, Long>> {
        val created = file.users.mapIndexed { i, u ->
            at("user.json の ${i + 1} 件目") {
                val member = write { users.register(u.email, u.name, u.password, Registrant.ByStaff(actor)) }
                if (u.status == SeedAccountStatus.SUSPENDED) write { users.suspend(EntityRef.Internal(member.id), u.suspendReason, actor) }
                member
            }
        }
        val result = SeedModuleResult(SeedModule.USER, created.map { SeedCreated(it.id, it.displayNumber, it.publicId) })
        return result to created.associate { it.email.value to it.id }
    }

    fun items(file: ItemSeedFile, actor: Actor.Staff): Pair<SeedModuleResult, Map<String, Long>> {
        val created = file.items.mapIndexed { i, def ->
            at("item.json の ${i + 1} 件目") {
                val item = write { items.create(def.name, def.description, def.price, actor) }
                val ref = EntityRef.Internal(item.id)
                if (def.stock != 0L) write { stock.adjust(ref, def.stock, INITIAL_STOCK_REASON, actor) }
                when (def.status) {
                    SeedItemStatus.UNPUBLISHED -> Unit
                    SeedItemStatus.PUBLISHED -> write { items.publish(ref, actor) }
                    SeedItemStatus.DELETED -> write { items.delete(ref, actor) }
                }
                item to def.status
            }
        }
        val result = SeedModuleResult(SeedModule.ITEM, created.map { (item, _) -> SeedCreated(item.id, item.displayNumber) })
        // 定義の確認で、参照される名前は削除済みでない商品の中で 1 つに決まることを確かめてある
        val byName = created.filter { (_, status) -> status != SeedItemStatus.DELETED }.associate { (item, _) -> item.name.value to item.id }
        return result to byName
    }

    fun orders(file: OrderSeedFile, refs: OrderRefs, actor: Actor.Staff): SeedModuleResult {
        val created = file.orders.mapIndexed { i, def ->
            at("order.json の ${i + 1} 件目") {
                val member = EntityRef.Internal(refs.members.getValue(normalizeEmail(def.member)))
                val lines = def.lines.map { OrderLineRequest(EntityRef.Internal(refs.items.getValue(itemKey(it))), it.quantity) }
                val order = write { orders.place(member, lines, actor) }
                val ref = EntityRef.Internal(order.id)
                def.steps.forEach { s ->
                    write {
                        when (s.op) {
                            SeedOrderOp.REQUEST_CANCEL -> orders.requestCancel(ref, s.reason, actor)
                            // 理由が要るかは core が確かめるので、無ければ空のまま渡す
                            SeedOrderOp.REJECT_CANCEL -> orders.rejectCancel(ref, s.reason ?: "", actor)
                            SeedOrderOp.APPROVE_CANCEL -> orders.approveCancel(ref, actor)
                            SeedOrderOp.COMPLETE -> orders.complete(ref, actor)
                        }
                    }
                }
                SeedCreated(order.id, order.displayNumber, order.publicId)
            }
        }
        return SeedModuleResult(SeedModule.ORDER, created)
    }

    fun notices(file: NoticeSeedFile, actor: Actor.Staff): SeedModuleResult {
        val now = clock.instant()
        val created = file.notices.mapIndexed { i, def ->
            at("notice.json の ${i + 1} 件目") {
                val start = def.start?.let { now.plus(SeedSource.parseOffset(it)!!) }
                val end = def.end?.let { now.plus(SeedSource.parseOffset(it)!!) }
                val notice = write { notices.create(def.title, def.body, start, end, actor) }
                val ref = EntityRef.Internal(notice.id)
                if (def.endNow) write { notices.end(ref, actor) }
                if (def.deleted) write { notices.delete(ref, actor) }
                SeedCreated(notice.id, notice.displayNumber)
            }
        }
        return SeedModuleResult(SeedModule.NOTICE, created)
    }

    /** seed order だけを実行するときに、参照を DB の会員と削除済みでない商品で解決する。 */
    fun resolveFromDb(file: OrderSeedFile, actor: Actor.Staff): OrderRefs {
        val repository = SqliteUserRepository()
        val emails = file.orders.map { normalizeEmail(it.member) }.distinct()
        val members = emails.associateWith { email ->
            val valid = runCatching { Email.of(email) }.getOrNull()
            val found = valid?.let { v -> db.read { repository.findByEmail(it, v) } }
            found?.member?.id ?: SeedSource.invalid(SeedModule.ORDER, "会員がいません: $email")
        }
        val names = file.orders.flatMap { o -> o.lines.map(::itemKey) }.distinct()
        val itemIds = names.associateWith { name ->
            val matches = items.list(ItemQuery(text = name, limit = PageRange.MAX_LIMIT), actor).items.filter { it.name.value == name }
            when (matches.size) {
                1 -> matches.single().id
                0 -> SeedSource.invalid(SeedModule.ORDER, "商品がありません（削除済みを除く）: $name")
                else -> SeedSource.invalid(SeedModule.ORDER, "商品名が ${matches.size} 件に当たります: $name")
            }
        }
        return OrderRefs(members, itemIds)
    }

    private inline fun <T> write(block: () -> T): T = block().also { written++ }

    private inline fun <T> at(where: String, block: () -> T): T = try {
        block()
    } catch (e: CoreException) {
        val left = if (written == 0) "まだ何も書いていません" else "それまでに書いた分は残っています。db reset --yes のあとにやり直してください"
        throw CoreException(e.kind, "${where}で失敗しました: ${e.message}。$left", e)
    }

    companion object {
        /** seed all で、注文の参照が同じ実行の定義で解決できるかを書く前に確かめる。 */
        fun checkRefs(orders: OrderSeedFile, users: UserSeedFile, items: ItemSeedFile) {
            val emails = users.users.map { normalizeEmail(it.email) }.toSet()
            val names = items.items.filter { it.status != SeedItemStatus.DELETED }.groupingBy { it.name.trim() }.eachCount()
            orders.orders.forEachIndexed { i, o ->
                if (normalizeEmail(o.member) !in emails) SeedSource.invalid(SeedModule.ORDER, "${i + 1} 件目の会員が user.json にいません: ${o.member}")
                o.lines.forEach { line ->
                    when (val count = names[itemKey(line)] ?: 0) {
                        1 -> Unit
                        0 -> SeedSource.invalid(SeedModule.ORDER, "${i + 1} 件目の商品が item.json にありません（削除済みを除く）: ${line.item}")
                        else -> SeedSource.invalid(SeedModule.ORDER, "${i + 1} 件目の商品名が item.json の $count 件に当たります: ${line.item}")
                    }
                }
            }
        }
    }
}

private fun itemKey(line: SeedOrderLine) = line.item.trim()
