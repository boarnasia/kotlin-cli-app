package io.github.boarnasia.cli

import io.github.boarnasia.core.db.Actor
import io.github.boarnasia.core.db.ActorNumbers
import io.github.boarnasia.core.db.CoreDb
import io.github.boarnasia.core.db.UtcTimestamps
import io.github.boarnasia.core.db.id.DisplayNumber
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

/** 人向けの表示で、操作した人を表示用の番号で出す。番号が引けない人（管理者の表が無かった頃の記録など）は内部 ID で出す。 */
class ActorLabels(private val numbers: Map<Actor, DisplayNumber>) {
    fun of(actor: ActorJson): String {
        val ref = actor.id.toLongOrNull()?.let { id -> runCatching { Actor.of(actor.type, id) }.getOrNull() }?.let(numbers::get) ?: actor.id
        return when (actor.type) {
            "staff" -> "管理者 $ref"
            "member" -> "会員本人 $ref"
            else -> "${actor.type} $ref"
        }
    }

    companion object {
        /** --json のときは表示しないので、呼ばれたときだけ core.db を読む。 */
        fun lookup(db: CoreDb, actors: Collection<Actor>) = ActorLabels(ActorNumbers.of(db, actors))
    }
}

fun jst(utc: String): String = Jst.format(UtcTimestamps.parse(utc))
