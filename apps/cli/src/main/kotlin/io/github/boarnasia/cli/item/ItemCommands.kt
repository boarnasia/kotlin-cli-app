package io.github.boarnasia.cli.item

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import io.github.boarnasia.cli.CliFailure
import io.github.boarnasia.cli.CliIo
import io.github.boarnasia.cli.DbCommand
import io.github.boarnasia.cli.ErrorCode
import io.github.boarnasia.cli.GroupCommand
import io.github.boarnasia.cli.NO_ID_GENERATOR
import io.github.boarnasia.cli.StaffActorCommand
import io.github.boarnasia.core.account.PageRange
import io.github.boarnasia.core.db.CoreDb
import io.github.boarnasia.core.db.id.EntityRef
import io.github.boarnasia.core.item.Item
import io.github.boarnasia.core.item.ItemQuery
import io.github.boarnasia.core.item.ItemService
import io.github.boarnasia.core.item.ItemStatus

fun itemCommand(io: CliIo) = GroupCommand("item", "商品を管理する").subcommands(
    ItemCreateCommand(io),
    ItemListCommand(io),
    ItemGetCommand(io),
    ItemUpdateCommand(io),
    ItemPublishCommand(io),
    ItemUnpublishCommand(io),
    ItemDeleteCommand(io),
)

private const val REF_HELP = "商品の内部 ID か表示用の番号（例 I-000001）"

/** 商品の引数。ID の形でなければ USAGE、ほかの種類の番号や UUID は core が VALIDATION にする。 */
fun DbCommand.itemRef() = argument("item", help = REF_HELP).convert { raw ->
    runCatching { EntityRef.parse(raw) }.getOrElse { fail(it.message ?: "商品の指定が正しくありません") }
}

private fun items(db: CoreDb) = ItemService(db, NO_ID_GENERATOR)

/** 商品を 1 つ返すコマンド。 */
abstract class ItemCommand(name: String, summary: String, io: CliIo) : StaffActorCommand(name, summary, io) {
    protected fun respondItem(db: CoreDb, item: Item) =
        respond(db, ItemJson.serializer(), ItemJson.of(item), listOf(item.createdBy, item.updatedBy), ItemJson::human)
}

class ItemCreateCommand(io: CliIo) : ItemCommand("create", "非公開の商品を登録する（item:write が要る）", io) {
    private val name by option("--name", help = "商品名（100 文字以内）").required()
    private val price by option("--price", help = "価格（円の整数、0〜1,000,000,000）").long().required()
    private val description by option("--description", help = "説明（2000 文字以内）")

    override fun execute() {
        val ids = idGenerator()
        val db = openExistingDb()
        respondItem(db, ItemService(db, ids).create(name, description, price, actor(db)))
    }
}

class ItemListCommand(io: CliIo) : StaffActorCommand("list", "商品の一覧を表示する（表示用の番号の昇順。省いたら削除済みを除く）", io) {
    private val status by option("--status", help = "状態で絞り込む").choice("unpublished", "published", "deleted")
    private val query by option("--query", help = "名前か説明の部分一致で絞り込む")
    private val limit by option("--limit", help = "件数（1〜${PageRange.MAX_LIMIT}）").int().default(PageRange.DEFAULT_LIMIT)
    private val offset by option("--offset", help = "読み飛ばす件数").int().default(0)

    override fun execute() {
        val db = openExistingDb()
        val page = items(db).list(
            ItemQuery(status = status?.let(ItemStatus::ofDbValue), text = query, limit = limit, offset = offset),
            actor(db),
        )
        val data = ItemListJson(page.items.map(ItemJson::of), page.total, page.limit, page.offset)
        respond(ItemListJson.serializer(), data) { list ->
            val rows = list.items.joinToString("\n", transform = ItemJson::row)
            val range = if (list.items.isEmpty()) "" else "（${list.offset + 1}〜${list.offset + list.items.size} 件目）"
            listOf(rows, "全 ${list.total} 件$range").filter { it.isNotEmpty() }.joinToString("\n")
        }
    }
}

class ItemGetCommand(io: CliIo) : ItemCommand("get", "商品を 1 つ表示する（削除済みも表示する）", io) {
    private val ref by itemRef()

    override fun execute() {
        val db = openExistingDb()
        respondItem(db, items(db).get(ref, actor(db)))
    }
}

class ItemUpdateCommand(io: CliIo) : ItemCommand("update", "商品の名前・説明・価格を変える（item:write が要る）", io) {
    private val ref by itemRef()
    private val name by option("--name", help = "新しい商品名")
    private val price by option("--price", help = "新しい価格（円の整数）").long()
    private val description by option("--description", help = "新しい説明（\"\" で消す）")

    override fun execute() {
        val db = openExistingDb()
        respondItem(db, items(db).update(ref, name, description, price, actor(db)))
    }
}

class ItemPublishCommand(io: CliIo) : ItemCommand("publish", "非公開の商品を公開する（item:write が要る）", io) {
    private val ref by itemRef()

    override fun execute() {
        val db = openExistingDb()
        respondItem(db, items(db).publish(ref, actor(db)))
    }
}

class ItemUnpublishCommand(io: CliIo) : ItemCommand("unpublish", "公開中の商品を非公開にする（item:write が要る）", io) {
    private val ref by itemRef()

    override fun execute() {
        val db = openExistingDb()
        respondItem(db, items(db).unpublish(ref, actor(db)))
    }
}

class ItemDeleteCommand(io: CliIo) : ItemCommand("delete", "商品を削除済みにする。戻せない（item:write が要る）", io) {
    private val ref by itemRef()
    private val yes by option("--yes", help = "商品を戻せないことを確認した").flag()

    override fun execute() {
        if (!yes) throw CliFailure(ErrorCode.USAGE, "item delete は商品を戻せない形で削除します。実行するには --yes を付けてください")
        val db = openExistingDb()
        respondItem(db, items(db).delete(ref, actor(db)))
    }
}
