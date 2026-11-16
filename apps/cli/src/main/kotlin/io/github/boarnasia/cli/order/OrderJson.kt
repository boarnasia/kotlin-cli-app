package io.github.boarnasia.cli.order

import io.github.boarnasia.cli.ActorJson
import io.github.boarnasia.cli.ActorLabels
import io.github.boarnasia.cli.item.yen
import io.github.boarnasia.cli.jst
import io.github.boarnasia.core.db.Actor
import io.github.boarnasia.core.db.UtcTimestamps
import io.github.boarnasia.core.order.Order
import kotlinx.serialization.Serializable

@Serializable
data class OrderMemberJson(val id: String, val displayNumber: String)

@Serializable
data class OrderLineJson(
    val lineNo: Int,
    val itemId: String,
    val itemDisplayNumber: String,
    /** 注文した時点の商品名。 */
    val name: String,
    /** 注文した時点の単価（円）。 */
    val unitPrice: Long,
    val quantity: Long,
    val subtotal: Long,
)

@Serializable
data class OrderEventJson(
    /** placed / cancel_requested / cancel_rejected / cancel_approved / completed */
    val kind: String,
    val fromStatus: String?,
    val toStatus: String,
    val reason: String?,
    val createdAt: String,
    val createdBy: ActorJson,
)

@Serializable
data class OrderJson(
    val id: String,
    val displayNumber: String,
    val publicId: String,
    val member: OrderMemberJson,
    /** accepted / cancel_requested / cancelled / completed */
    val status: String,
    /** 円 */
    val total: Long,
    val lines: List<OrderLineJson>,
    val placedAt: String,
    val completedAt: String?,
    val cancelledAt: String?,
    /** 古い順。 */
    val events: List<OrderEventJson>,
    val createdAt: String,
    val updatedAt: String,
    val createdBy: ActorJson,
    val updatedBy: ActorJson,
) {
    fun human(labels: ActorLabels): String = buildList {
        add("$displayNumber  ${orderStatusLabel(status)}")
        add("  内部 ID:   $id")
        add("  公開 ID:   $publicId")
        add("  会員:      ${member.displayNumber}")
        add("  合計:      ${yen(total)}")
        add("  明細:")
        lines.forEach { add("    ${it.lineNo}. ${it.itemDisplayNumber}  ${it.name}  ${yen(it.unitPrice)} × ${it.quantity} = ${yen(it.subtotal)}") }
        add("  受付:      ${jst(placedAt)}")
        completedAt?.let { add("  完了:      ${jst(it)}") }
        cancelledAt?.let { add("  キャンセル: ${jst(it)}") }
        add("  登録:      ${jst(createdAt)}（${labels.of(createdBy)}）")
        add("  最終変更:  ${jst(updatedAt)}（${labels.of(updatedBy)}）")
        add("  履歴:")
        events.forEach { e ->
            val move = listOfNotNull(e.fromStatus?.let(::orderStatusLabel), orderStatusLabel(e.toStatus)).joinToString("→")
            add(listOfNotNull("    ${jst(e.createdAt)}", eventKindLabel(e.kind), move, e.reason, labels.of(e.createdBy)).joinToString("  "))
        }
    }.joinToString("\n")

    /** 一覧の 1 行。明細は最初の 1 行と残りの件数だけを出す。 */
    fun row(): String {
        val first = lines.first()
        val rest = if (lines.size > 1) " ほか ${lines.size - 1} 件" else ""
        return "$displayNumber  ${orderStatusLabel(status)}  ${yen(total)}  ${member.displayNumber}  ${jst(placedAt)}  ${first.name}×${first.quantity}$rest"
    }

    companion object {
        fun of(order: Order) = OrderJson(
            id = order.id.toString(),
            displayNumber = order.displayNumber.toString(),
            publicId = order.publicId.toString(),
            member = OrderMemberJson(order.member.id.toString(), order.member.displayNumber.toString()),
            status = order.status.dbValue,
            total = order.total,
            lines = order.lines.map {
                OrderLineJson(it.lineNo, it.itemId.toString(), it.itemDisplayNumber.toString(), it.itemName, it.unitPrice, it.quantity, it.subtotal)
            },
            placedAt = UtcTimestamps.format(order.placedAt),
            completedAt = order.completedAt?.let(UtcTimestamps::format),
            cancelledAt = order.cancelledAt?.let(UtcTimestamps::format),
            events = order.events.map {
                OrderEventJson(
                    kind = it.kind.dbValue,
                    fromStatus = it.fromStatus?.dbValue,
                    toStatus = it.toStatus.dbValue,
                    reason = it.reason?.value,
                    createdAt = UtcTimestamps.format(it.createdAt),
                    createdBy = ActorJson.of(it.createdBy),
                )
            },
            createdAt = UtcTimestamps.format(order.createdAt),
            updatedAt = UtcTimestamps.format(order.updatedAt),
            createdBy = ActorJson.of(order.createdBy),
            updatedBy = ActorJson.of(order.updatedBy),
        )

        /** 人向けの表示で番号を引く、注文に出てくる操作した人。 */
        fun actorsOf(order: Order): List<Actor> = listOf(order.createdBy, order.updatedBy) + order.events.map { it.createdBy }
    }
}

@Serializable
data class OrderListJson(val items: List<OrderJson>, val total: Int, val limit: Int, val offset: Int)

fun orderStatusLabel(status: String) = when (status) {
    "accepted" -> "受付済み"
    "cancel_requested" -> "キャンセル申請中"
    "cancelled" -> "キャンセル済み"
    "completed" -> "完了"
    else -> status
}

fun eventKindLabel(kind: String) = when (kind) {
    "placed" -> "作成"
    "cancel_requested" -> "キャンセルの申請"
    "cancel_rejected" -> "キャンセルの却下"
    "cancel_approved" -> "キャンセルの承認"
    "completed" -> "完了"
    else -> kind
}
