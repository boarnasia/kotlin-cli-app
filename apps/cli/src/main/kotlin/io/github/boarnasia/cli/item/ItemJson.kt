package io.github.boarnasia.cli.item

import io.github.boarnasia.cli.ActorJson
import io.github.boarnasia.cli.ActorLabels
import io.github.boarnasia.cli.jst
import io.github.boarnasia.core.db.UtcTimestamps
import io.github.boarnasia.core.item.Item
import io.github.boarnasia.core.item.StockLevel
import java.text.NumberFormat
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data class ItemStockJson(val onHand: Long, val reserved: Long, val available: Long) {
    companion object {
        fun of(stock: StockLevel) = ItemStockJson(stock.onHand, stock.reserved, stock.available)
    }
}

@Serializable
data class ItemJson(
    val id: String,
    val displayNumber: String,
    val name: String,
    val description: String?,
    /** 円 */
    val price: Long,
    /** unpublished / published / deleted */
    val status: String,
    val publishedAt: String?,
    val deletedAt: String?,
    val stock: ItemStockJson,
    val createdAt: String,
    val updatedAt: String,
    val createdBy: ActorJson,
    val updatedBy: ActorJson,
) {
    fun human(labels: ActorLabels): String = buildList {
        add("$displayNumber  ${itemStatusLabel(status)}")
        add("  内部 ID:   $id")
        add("  名前:      $name")
        add("  説明:      ${description ?: "（なし）"}")
        add("  価格:      ${yen(price)}")
        add("  在庫:      在庫数 ${stock.onHand}・引当 ${stock.reserved}・注文可能数 ${stock.available}")
        publishedAt?.let { add("  公開:      ${jst(it)}") }
        deletedAt?.let { add("  削除:      ${jst(it)}") }
        add("  登録:      ${jst(createdAt)}（${labels.of(createdBy)}）")
        add("  最終変更:  ${jst(updatedAt)}（${labels.of(updatedBy)}）")
    }.joinToString("\n")

    /** 一覧の 1 行。 */
    fun row(): String =
        "$displayNumber  ${itemStatusLabel(status)}  ${yen(price)}  在庫 ${stock.onHand}（注文可能 ${stock.available}）  $name"

    companion object {
        fun of(item: Item) = ItemJson(
            id = item.id.toString(),
            displayNumber = item.displayNumber.toString(),
            name = item.name.value,
            description = item.description?.value,
            price = item.price.yen,
            status = item.status.dbValue,
            publishedAt = item.publishedAt?.let(UtcTimestamps::format),
            deletedAt = item.deletedAt?.let(UtcTimestamps::format),
            stock = ItemStockJson.of(item.stock),
            createdAt = UtcTimestamps.format(item.createdAt),
            updatedAt = UtcTimestamps.format(item.updatedAt),
            createdBy = ActorJson.of(item.createdBy),
            updatedBy = ActorJson.of(item.updatedBy),
        )
    }
}

@Serializable
data class ItemListJson(val items: List<ItemJson>, val total: Int, val limit: Int, val offset: Int)

fun itemStatusLabel(status: String) = when (status) {
    "unpublished" -> "非公開"
    "published" -> "公開"
    "deleted" -> "削除済み"
    else -> status
}

fun yen(value: Long): String = NumberFormat.getIntegerInstance(Locale.JAPAN).format(value) + " 円"
