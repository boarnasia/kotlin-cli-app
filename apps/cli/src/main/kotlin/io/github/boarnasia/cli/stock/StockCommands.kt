package io.github.boarnasia.cli.stock

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import io.github.boarnasia.cli.CliIo
import io.github.boarnasia.cli.GroupCommand
import io.github.boarnasia.cli.NO_ID_GENERATOR
import io.github.boarnasia.cli.StaffActorCommand
import io.github.boarnasia.cli.item.itemRef
import io.github.boarnasia.core.account.PageRange
import io.github.boarnasia.core.db.OrderNumbers
import io.github.boarnasia.core.item.MovementQuery
import io.github.boarnasia.core.item.StockService

fun stockCommand(io: CliIo) = GroupCommand("stock", "商品の在庫を確認・調整する").subcommands(
    StockShowCommand(io),
    StockAdjustCommand(io),
    StockHistoryCommand(io),
)

class StockShowCommand(io: CliIo) : StaffActorCommand("show", "商品の在庫数・引当・注文可能数を表示する", io) {
    private val ref by itemRef()

    override fun execute() {
        val db = openExistingDb()
        val stock = StockService(db, NO_ID_GENERATOR).show(ref, actor(db))
        respond(StockJson.serializer(), StockJson.of(stock), StockJson::human)
    }
}

class StockAdjustCommand(io: CliIo) : StaffActorCommand("adjust", "在庫数を増減する（入荷・棚卸しの差など。item:write が要る）", io) {
    private val ref by itemRef()
    private val delta by option("--delta", help = "在庫数の増減（0 以外の整数。減らすときは負の数）").long().required()
    private val reason by option("--reason", help = "理由（500 文字以内）").required()

    override fun execute() {
        val ids = idGenerator()
        val db = openExistingDb()
        val result = StockService(db, ids).adjust(ref, delta, reason, actor(db))
        val data = StockAdjustJson(StockJson.of(result.stock), MovementJson.of(result.movement))
        respond(db, StockAdjustJson.serializer(), data, listOf(result.movement.createdBy)) { it, labels ->
            listOf(it.stock.human(), "変動: ${it.movement.row(labels)}").joinToString("\n")
        }
    }
}

class StockHistoryCommand(io: CliIo) : StaffActorCommand("history", "在庫の変動の履歴を新しい順に表示する", io) {
    private val ref by itemRef()
    private val limit by option("--limit", help = "件数（1〜${PageRange.MAX_LIMIT}）").int().default(PageRange.DEFAULT_LIMIT)
    private val offset by option("--offset", help = "読み飛ばす件数").int().default(0)

    override fun execute() {
        val db = openExistingDb()
        val page = StockService(db, NO_ID_GENERATOR).history(ref, MovementQuery(limit = limit, offset = offset), actor(db))
        val data = MovementListJson(page.items.map(MovementJson::of), page.total, page.limit, page.offset)
        respond(db, MovementListJson.serializer(), data, page.items.map { it.createdBy }) { list, labels ->
            val orderNumbers = OrderNumbers.of(db, page.items.mapNotNull { it.orderId }).entries.associate { (id, n) -> id.toString() to n.toString() }
            val rows = list.items.joinToString("\n") { it.row(labels, orderNumbers) }
            val range = if (list.items.isEmpty()) "" else "（${list.offset + 1}〜${list.offset + list.items.size} 件目）"
            listOf(rows, "全 ${list.total} 件$range").filter { it.isNotEmpty() }.joinToString("\n")
        }
    }
}
