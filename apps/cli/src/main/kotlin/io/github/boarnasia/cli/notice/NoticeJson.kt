package io.github.boarnasia.cli.notice

import io.github.boarnasia.cli.ActorJson
import io.github.boarnasia.cli.ActorLabels
import io.github.boarnasia.cli.jst
import io.github.boarnasia.core.db.UtcTimestamps
import io.github.boarnasia.core.notice.Notice
import kotlinx.serialization.Serializable

@Serializable
data class NoticeJson(
    val id: String,
    val displayNumber: String,
    val title: String,
    val body: String,
    val status: String,
    val publishStartAt: String?,
    val publishEndAt: String?,
    val deletedAt: String?,
    val createdAt: String,
    val updatedAt: String,
    val createdBy: ActorJson,
    val updatedBy: ActorJson,
) {
    private fun statusLabel() = if (deletedAt != null) "削除済み" else when (status) {
        "draft" -> "下書き"
        "scheduled" -> "予約"
        "active" -> "公開中"
        "ended" -> "終了"
        else -> status
    }
    private fun period() = "${publishStartAt?.let(::jst) ?: "（開始なし）"} 〜 ${publishEndAt?.let(::jst) ?: "（期限なし）"}"
    fun row() = "$displayNumber  ${statusLabel()}  ${period()}  ${title.replace(Regex("[\\r\\n]+"), " ")}"
    fun human(labels: ActorLabels): String = buildList {
        add("$displayNumber  ${statusLabel()}")
        add("  内部 ID:   $id")
        add("  題名:      $title")
        add("  本文:      $body")
        add("  公開期間:  ${period()}")
        deletedAt?.let { add("  削除:      ${jst(it)}") }
        add("  登録:      ${jst(createdAt)}（${labels.of(createdBy)}）")
        add("  最終変更:  ${jst(updatedAt)}（${labels.of(updatedBy)}）")
    }.joinToString("\n")
    companion object {
        fun of(n: Notice) = NoticeJson(
            n.id.toString(), n.displayNumber.toString(), n.title.value, n.body.value, n.status.dbValue,
            n.publishStartAt?.let(UtcTimestamps::format), n.publishEndAt?.let(UtcTimestamps::format), n.deletedAt?.let(UtcTimestamps::format),
            UtcTimestamps.format(n.createdAt), UtcTimestamps.format(n.updatedAt), ActorJson.of(n.createdBy), ActorJson.of(n.updatedBy),
        )
    }
}
@Serializable
data class NoticeListJson(val items: List<NoticeJson>, val total: Int, val limit: Int, val offset: Int)
@Serializable
data class NoticeVisibleJson(val at: String, val items: List<NoticeJson>)
