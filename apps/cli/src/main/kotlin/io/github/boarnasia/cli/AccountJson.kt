package io.github.boarnasia.cli

import io.github.boarnasia.core.db.Actor
import io.github.boarnasia.core.db.UtcTimestamps
import kotlinx.serialization.Serializable

/** 操作した人。管理者と会員の内部 ID は別の表の主キーなので、型と組で出す。 */
@Serializable
data class ActorJson(
    /** staff / member */
    val type: String,
    val id: String,
) {
    companion object {
        fun of(actor: Actor) = ActorJson(type = actor.type.dbValue, id = actor.id.toString())
    }
}

/** 会員と管理者の状態の人向けの表示。 */
fun statusLabel(status: String) = when (status) {
    "active" -> "有効"
    "suspended" -> "停止"
    else -> status
}

fun actorLabel(actor: ActorJson) = when (actor.type) {
    "staff" -> "管理者 ${actor.id}"
    "member" -> "会員本人 ${actor.id}"
    else -> "${actor.type} ${actor.id}"
}

fun jst(utc: String): String = Jst.format(UtcTimestamps.parse(utc))
