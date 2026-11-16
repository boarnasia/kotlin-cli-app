package io.github.boarnasia.cli.order

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import io.github.boarnasia.cli.CliIo
import io.github.boarnasia.cli.GroupCommand
import io.github.boarnasia.cli.NO_ID_GENERATOR
import io.github.boarnasia.cli.StaffActorCommand
import io.github.boarnasia.core.account.PageRange
import io.github.boarnasia.core.db.CoreDb
import io.github.boarnasia.core.db.id.EntityRef
import io.github.boarnasia.core.order.Order
import io.github.boarnasia.core.order.OrderLineRequest
import io.github.boarnasia.core.order.OrderQuery
import io.github.boarnasia.core.order.OrderService
import io.github.boarnasia.core.order.OrderStatus

fun orderCommand(io: CliIo) = GroupCommand("order", "注文を管理する").subcommands(
    OrderPlaceCommand(io),
    OrderListCommand(io),
    OrderGetCommand(io),
    OrderRequestCancelCommand(io),
    OrderApproveCancelCommand(io),
    OrderRejectCancelCommand(io),
    OrderCompleteCommand(io),
)

private const val REF_HELP = "注文の内部 ID・表示用の番号（例 O-000001）・公開 ID（UUID）のどれか"
private const val USER_HELP = "会員の内部 ID・表示用の番号（例 M-000001）・公開 ID（UUID）のどれか"

private fun orders(db: CoreDb) = OrderService(db, NO_ID_GENERATOR)

private fun parseRef(raw: String, what: String): EntityRef =
    runCatching { EntityRef.parse(raw) }.getOrElse { throw IllegalArgumentException(it.message ?: "${what}の指定が正しくありません") }

/** 注文を 1 つ返すコマンド。 */
abstract class OrderCommand(name: String, summary: String, io: CliIo) : StaffActorCommand(name, summary, io) {
    protected val ref by argument("order", help = REF_HELP).convert { raw ->
        runCatching { parseRef(raw, "注文") }.getOrElse { fail(it.message!!) }
    }

    protected fun respondOrder(db: CoreDb, order: Order) =
        respond(db, OrderJson.serializer(), OrderJson.of(order), OrderJson.actorsOf(order), OrderJson::human)
}

class OrderPlaceCommand(io: CliIo) : StaffActorCommand("place", "会員の代わりに注文し、在庫を引き当てる（order:write が要る）", io) {
    private val user by option("--user", help = USER_HELP).convert { raw ->
        runCatching { parseRef(raw, "会員") }.getOrElse { fail(it.message!!) }
    }.required()

    // 商品と数を組で受け取り、別々のオプションにしたときの並びの取り違えを防ぐ
    private val lines by option("--item", help = "商品と数を「商品:数」で指定する（例 I-000001:2。繰り返して複数の明細にする）")
        .convert { raw ->
            val separator = raw.lastIndexOf(':')
            if (separator <= 0) fail("--item は「商品:数」の形で指定してください（例 I-000001:2）: $raw")
            val item = runCatching { parseRef(raw.substring(0, separator), "商品") }.getOrElse { fail(it.message!!) }
            val quantity = raw.substring(separator + 1).toLongOrNull() ?: fail("--item の数は整数で指定してください: $raw")
            OrderLineRequest(item, quantity)
        }
        .multiple(required = true)

    override fun execute() {
        val ids = idGenerator()
        val db = openExistingDb()
        val order = OrderService(db, ids).place(user, lines, actor(db))
        respond(db, OrderJson.serializer(), OrderJson.of(order), OrderJson.actorsOf(order), OrderJson::human)
    }
}

class OrderListCommand(io: CliIo) : StaffActorCommand("list", "注文の一覧を、受け付けた時刻の新しい順に表示する（order:read が要る）", io) {
    private val status by option("--status", help = "状態で絞り込む").choice(*OrderStatus.entries.map { it.dbValue }.toTypedArray())
    private val user by option("--user", help = "会員で絞り込む（$USER_HELP）").convert { raw ->
        runCatching { parseRef(raw, "会員") }.getOrElse { fail(it.message!!) }
    }
    private val limit by option("--limit", help = "件数（1〜${PageRange.MAX_LIMIT}）").int().default(PageRange.DEFAULT_LIMIT)
    private val offset by option("--offset", help = "読み飛ばす件数").int().default(0)

    override fun execute() {
        val db = openExistingDb()
        val page = orders(db).list(
            OrderQuery(status = status?.let(OrderStatus::ofDbValue), member = user, limit = limit, offset = offset),
            actor(db),
        )
        val data = OrderListJson(page.items.map(OrderJson::of), page.total, page.limit, page.offset)
        respond(OrderListJson.serializer(), data) { list ->
            val rows = list.items.joinToString("\n", transform = OrderJson::row)
            val range = if (list.items.isEmpty()) "" else "（${list.offset + 1}〜${list.offset + list.items.size} 件目）"
            listOf(rows, "全 ${list.total} 件$range").filter { it.isNotEmpty() }.joinToString("\n")
        }
    }
}

class OrderGetCommand(io: CliIo) : OrderCommand("get", "注文を 1 つ、明細と履歴と一緒に表示する（order:read が要る）", io) {
    override fun execute() {
        val db = openExistingDb()
        respondOrder(db, orders(db).get(ref, actor(db)))
    }
}

class OrderRequestCancelCommand(io: CliIo) : OrderCommand("request-cancel", "会員の代わりにキャンセルを申請する（order:write が要る）", io) {
    private val reason by option("--reason", help = "申請の理由（省ける。500 文字以内）")

    override fun execute() {
        val ids = idGenerator()
        val db = openExistingDb()
        respondOrder(db, OrderService(db, ids).requestCancel(ref, reason, actor(db)))
    }
}

class OrderApproveCancelCommand(io: CliIo) : OrderCommand("approve-cancel", "キャンセルを承認し、引当を戻す（order:cancel-approve が要る）", io) {
    override fun execute() {
        val ids = idGenerator()
        val db = openExistingDb()
        respondOrder(db, OrderService(db, ids).approveCancel(ref, actor(db)))
    }
}

class OrderRejectCancelCommand(io: CliIo) : OrderCommand("reject-cancel", "キャンセルを却下し、受付済みに戻す（order:cancel-approve が要る）", io) {
    private val reason by option("--reason", help = "却下の理由（必須。500 文字以内。会員に伝える）").required()

    override fun execute() {
        val ids = idGenerator()
        val db = openExistingDb()
        respondOrder(db, OrderService(db, ids).rejectCancel(ref, reason, actor(db)))
    }
}

class OrderCompleteCommand(io: CliIo) : OrderCommand("complete", "注文を完了にし、出荷する（order:write が要る）", io) {
    override fun execute() {
        val ids = idGenerator()
        val db = openExistingDb()
        respondOrder(db, OrderService(db, ids).complete(ref, actor(db)))
    }
}
