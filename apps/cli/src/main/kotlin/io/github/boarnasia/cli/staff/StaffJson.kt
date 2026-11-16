package io.github.boarnasia.cli.staff

import io.github.boarnasia.cli.ActorJson
import io.github.boarnasia.cli.actorLabel
import io.github.boarnasia.cli.jst
import io.github.boarnasia.cli.statusLabel
import io.github.boarnasia.core.db.UtcTimestamps
import io.github.boarnasia.core.staff.Staff
import kotlinx.serialization.Serializable

@Serializable
data class StaffJson(
    val id: String,
    val displayNumber: String,
    val email: String,
    val name: String,
    /** active / suspended */
    val status: String,
    /** 持つ権限だけを、core の Permission の順に並べる。 */
    val permissions: List<String>,
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
        add("  メール:    $email")
        add("  名前:      $name")
        add("  権限:      ${permissionsLabel()}")
        suspendedAt?.let { add("  停止:      ${jst(it)}${suspensionReason?.let { r -> "（$r）" } ?: ""}") }
        add("  登録:      ${jst(createdAt)}（${actorLabel(createdBy)}）")
        add("  最終変更:  ${jst(updatedAt)}（${actorLabel(updatedBy)}）")
    }.joinToString("\n")

    fun permissionsLabel(): String = permissions.joinToString(",").ifEmpty { "（なし）" }

    companion object {
        fun of(staff: Staff) = StaffJson(
            id = staff.id.toString(),
            displayNumber = staff.displayNumber.toString(),
            email = staff.email.value,
            name = staff.name.value,
            status = staff.status.dbValue,
            permissions = staff.permissions.sorted().map { it.code },
            suspendedAt = staff.suspendedAt?.let(UtcTimestamps::format),
            suspensionReason = staff.suspensionReason?.value,
            createdAt = UtcTimestamps.format(staff.createdAt),
            updatedAt = UtcTimestamps.format(staff.updatedAt),
            createdBy = ActorJson.of(staff.createdBy),
            updatedBy = ActorJson.of(staff.updatedBy),
        )
    }
}

@Serializable
data class StaffListJson(val items: List<StaffJson>, val total: Int, val limit: Int, val offset: Int)

@Serializable
data class StaffAuthenticateJson(val authenticated: Boolean, val staff: StaffJson)
