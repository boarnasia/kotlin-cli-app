package io.github.boarnasia.cli.seed

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.github.boarnasia.cli.CliIo
import io.github.boarnasia.cli.CoreCommand
import io.github.boarnasia.cli.DbCommand
import io.github.boarnasia.cli.GroupCommand
import io.github.boarnasia.cli.StaffActorCommand
import io.github.boarnasia.core.db.Actor

fun seedCommand(io: CliIo) = GroupCommand("seed", "定義ファイルから決まった内容を投入する（staff → user → item → order → notice の順）").subcommands(
    SeedAllCommand(io),
    SeedStaffCommand(io),
    SeedUserCommand(io),
    SeedItemCommand(io),
    SeedOrderCommand(io),
    SeedNoticeCommand(io),
)

private fun CoreCommand.dirOption() =
    option("--dir", help = "定義ファイル（staff.json など）を置いたディレクトリ。省いたら CLI に同梱した既定の定義を使う").path()

class SeedAllCommand(io: CliIo) : DbCommand("all", "空の DB に、全てのモジュールを順に投入する（--actor は取らない）", io) {
    private val dir by dirOption()

    override fun execute() {
        val ids = idGenerator()
        val db = openExistingDb()
        val source = SeedSource(dir)
        val staff = source.staff()
        val users = source.users()
        val items = source.items()
        val orders = source.orders()
        val notices = source.notices()
        Seeder.checkRefs(orders, users, items)
        val seeder = Seeder(db, ids)
        seeder.requireEmpty(SeedModule.entries)

        val (actor, staffResult) = seeder.staff(staff)
        val (userResult, members) = seeder.users(users, actor)
        val (itemResult, itemIds) = seeder.items(items, actor)
        val orderResult = seeder.orders(orders, OrderRefs(members, itemIds), actor)
        val noticeResult = seeder.notices(notices, actor)
        respond(SeedJson.serializer(), SeedJson.of(listOf(staffResult, userResult, itemResult, orderResult, noticeResult)), SeedJson::human)
    }
}

class SeedStaffCommand(io: CliIo) : DbCommand("staff", "管理者のいない DB に、最初の管理者（bootstrap）と残りの管理者を投入する（--actor は取らない）", io) {
    private val dir by dirOption()

    override fun execute() {
        val ids = idGenerator()
        val db = openExistingDb()
        val file = SeedSource(dir).staff()
        val seeder = Seeder(db, ids)
        seeder.requireEmpty(listOf(SeedModule.STAFF))
        val (_, result) = seeder.staff(file)
        respond(SeedJson.serializer(), SeedJson.of(listOf(result)), SeedJson::human)
    }
}

/** 管理者として 1 つのモジュールを投入するコマンド。要る権限は、各モジュールの登録・変更のコマンドと同じ。 */
abstract class SeedModuleCommand(private val module: SeedModule, summary: String, io: CliIo) : StaffActorCommand(module.key, summary, io) {
    private val dir by dirOption()

    final override fun execute() {
        val ids = idGenerator()
        val db = openExistingDb()
        val source = SeedSource(dir)
        val load = prepare(source)
        val seeder = Seeder(db, ids)
        seeder.requireEmpty(listOf(module))
        val result = load(seeder, actor(db))
        respond(SeedJson.serializer(), SeedJson.of(listOf(result)), SeedJson::human)
    }

    /** 定義を読んで形を確かめ、投入する処理を返す。DB に触る前に定義の誤りで止めるため、読むのと投入を分ける。 */
    protected abstract fun prepare(source: SeedSource): (Seeder, Actor.Staff) -> SeedModuleResult
}

class SeedUserCommand(io: CliIo) : SeedModuleCommand(SeedModule.USER, "会員のいない DB に会員を投入する（member:write が要る）", io) {
    override fun prepare(source: SeedSource): (Seeder, Actor.Staff) -> SeedModuleResult {
        val file = source.users()
        return { seeder, actor -> seeder.users(file, actor).first }
    }
}

class SeedItemCommand(io: CliIo) : SeedModuleCommand(SeedModule.ITEM, "商品のいない DB に商品と初期在庫を投入する（item:write が要る）", io) {
    override fun prepare(source: SeedSource): (Seeder, Actor.Staff) -> SeedModuleResult {
        val file = source.items()
        return { seeder, actor -> seeder.items(file, actor).first }
    }
}

class SeedOrderCommand(
    io: CliIo,
) : SeedModuleCommand(SeedModule.ORDER, "注文のいない DB に注文を投入する。会員と商品は DB から引く（order:write と、却下・承認があれば order:cancel-approve が要る）", io) {
    override fun prepare(source: SeedSource): (Seeder, Actor.Staff) -> SeedModuleResult {
        val file = source.orders()
        return { seeder, actor -> seeder.orders(file, seeder.resolveFromDb(file, actor), actor) }
    }
}

class SeedNoticeCommand(io: CliIo) : SeedModuleCommand(SeedModule.NOTICE, "お知らせのいない DB にお知らせを投入する（notice:write が要る）", io) {
    override fun prepare(source: SeedSource): (Seeder, Actor.Staff) -> SeedModuleResult {
        val file = source.notices()
        return { seeder, actor -> seeder.notices(file, actor) }
    }
}
