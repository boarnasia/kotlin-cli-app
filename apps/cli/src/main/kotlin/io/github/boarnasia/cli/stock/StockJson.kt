package io.github.boarnasia.cli.stock

import io.github.boarnasia.cli.ActorJson
import io.github.boarnasia.cli.ActorLabels
import io.github.boarnasia.cli.jst
import io.github.boarnasia.core.db.UtcTimestamps
import io.github.boarnasia.core.item.StockLevel
import io.github.boarnasia.core.item.StockMovement
import kotlinx.serialization.Serializable

@Serializable
data class StockJson(
    val itemId: String,
    /** 商品の I- の番号。 */
    val displayNumber: String,
    val onHand: Long,
    val reserved: Long,
    val available: Long,
    val updatedAt: String,
) {
    fun human(): String = listOf(
        "$displayNumber の在庫",
        "  在庫数:      $onHand",
        "  引当:        $reserved",
        "  注文可能数:  $available",
        "  更新:        ${jst(updatedAt)}",
    ).joinToString("\n")

    companion object {
        fun of(stock: StockLevel) = StockJson(
            itemId = stock.itemId.toString(),
            displayNumber = stock.itemDisplayNumber.toString(),
            onHand = stock.onHand,
            reserved = stock.reserved,
            available = stock.available,
            updatedAt = UtcTimestamps.format(stock.updatedAt),
        )
    }
}

@Serializable
data class MovementJson(
    val id: String,
    val itemId: String,
    /** adjust / reserve / release / ship */
    val kind: String,
    val onHandDelta: Long,
    val reservedDelta: Long,
    val onHandAfter: Long,
    val reservedAfter: Long,
    val reason: String?,
    val orderId: String?,
    val createdAt: String,
    val createdBy: ActorJson,
) {
    /** 履歴の 1 行。注文は [orderNumbers]（内部 ID → O- の番号）で出し、引けなければ内部 ID で出す。 */
    fun row(labels: ActorLabels, orderNumbers: Map<String, String> = emptyMap()): String = listOfNotNull(
        jst(createdAt),
        kindLabel(kind),
        "在庫数 ${signed(onHandDelta)}→$onHandAfter",
        "引当 ${signed(reservedDelta)}→$reservedAfter",
        reason,
        orderId?.let { "注文 ${orderNumbers[it] ?: it}" },
        labels.of(createdBy),
    ).joinToString("  ")

    companion object {
        fun of(m: StockMovement) = MovementJson(
            id = m.id.toString(),
            itemId = m.itemId.toString(),
            kind = m.kind.dbValue,
            onHandDelta = m.onHandDelta,
            reservedDelta = m.reservedDelta,
            onHandAfter = m.onHandAfter,
            reservedAfter = m.reservedAfter,
            reason = m.reason?.value,
            orderId = m.orderId?.toString(),
            createdAt = UtcTimestamps.format(m.createdAt),
            createdBy = ActorJson.of(m.createdBy),
        )
    }
}

@Serializable
data class StockAdjustJson(val stock: StockJson, val movement: MovementJson)

@Serializable
data class MovementListJson(val items: List<MovementJson>, val total: Int, val limit: Int, val offset: Int)

fun kindLabel(kind: String) = when (kind) {
    "adjust" -> "調整"
    "reserve" -> "引当"
    "release" -> "戻し"
    "ship" -> "出荷"
    else -> kind
}

private fun signed(value: Long) = if (value > 0) "+$value" else value.toString()
