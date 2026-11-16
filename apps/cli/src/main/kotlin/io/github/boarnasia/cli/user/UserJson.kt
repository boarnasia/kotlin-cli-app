package io.github.boarnasia.cli.user

import io.github.boarnasia.cli.ActorJson
import io.github.boarnasia.cli.actorLabel
import io.github.boarnasia.cli.jst
import io.github.boarnasia.cli.statusLabel
import io.github.boarnasia.core.db.UtcTimestamps
import io.github.boarnasia.core.user.Member
import kotlinx.serialization.Serializable

@Serializable
data class MemberJson(
    val id: String,
    val displayNumber: String,
    val publicId: String,
    val email: String,
    val name: String,
    /** active / suspended */
    val status: String,
    val suspendedAt: String?,
    val suspensionReason: String?,
    val createdAt: String,
    val updatedAt: String,
    val createdBy: ActorJson,
    val updatedBy: ActorJson,
) {
    fun human(): String = buildList {
        add("$displayNumber  ${statusLabel(status)}")
        add("  内部 ID:   $id")
        add("  公開 ID:   $publicId")
        add("  メール:    $email")
        add("  名前:      $name")
        suspendedAt?.let { add("  停止:      ${jst(it)}${suspensionReason?.let { r -> "（$r）" } ?: ""}") }
        add("  登録:      ${jst(createdAt)}（${actorLabel(createdBy)}）")
        add("  最終変更:  ${jst(updatedAt)}（${actorLabel(updatedBy)}）")
    }.joinToString("\n")

    companion object {
        fun of(member: Member) = MemberJson(
            id = member.id.toString(),
            displayNumber = member.displayNumber.toString(),
            publicId = member.publicId.toString(),
            email = member.email.value,
            name = member.name.value,
            status = member.status.dbValue,
            suspendedAt = member.suspendedAt?.let(UtcTimestamps::format),
            suspensionReason = member.suspensionReason?.value,
            createdAt = UtcTimestamps.format(member.createdAt),
            updatedAt = UtcTimestamps.format(member.updatedAt),
            createdBy = ActorJson.of(member.createdBy),
            updatedBy = ActorJson.of(member.updatedBy),
        )
    }
}

@Serializable
data class MemberListJson(val items: List<MemberJson>, val total: Int, val limit: Int, val offset: Int)

@Serializable
data class AuthenticateJson(val authenticated: Boolean, val user: MemberJson)
