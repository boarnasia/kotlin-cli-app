package io.github.boarnasia.cli.db

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.github.boarnasia.cli.CliFailure
import io.github.boarnasia.cli.CliIo
import io.github.boarnasia.cli.DbCommand
import io.github.boarnasia.cli.ErrorCode
import io.github.boarnasia.cli.GroupCommand
import io.github.boarnasia.cli.Jst
import io.github.boarnasia.core.db.CoreMigrations
import io.github.boarnasia.core.db.MigrateResult
import io.github.boarnasia.core.db.MigrationRow
import io.github.boarnasia.core.db.MigrationState
import io.github.boarnasia.core.db.UtcTimestamps
import java.nio.file.Path
import kotlinx.serialization.Serializable

fun dbCommand(io: CliIo) = GroupCommand("db", "core.db のスキーマを管理する")
    .subcommands(DbMigrateCommand(io), DbStatusCommand(io), DbResetCommand(io))

class DbMigrateCommand(io: CliIo) : DbCommand("migrate", "未適用のマイグレーションを適用する。core.db が無ければ作る", io) {
    override fun execute() {
        val result = CoreMigrations(openOrCreateDb()).migrate()
        respond(DbMigrateJson.serializer(), DbMigrateJson.of(absoluteDbPath, result)) { data ->
            buildString {
                appendLine("core.db: ${data.path}")
                if (data.appliedCount == 0) append("適用するマイグレーションはありません") else append("${data.appliedCount} 件を適用しました")
                appendLine("（現在のバージョン: ${data.currentVersion ?: "なし"}）")
                append(result.applied.joinToString("\n", transform = ::humanRow))
            }.trimEnd()
        }
    }
}

class DbStatusCommand(io: CliIo) : DbCommand("status", "マイグレーションの適用状況を表示する", io) {
    override fun execute() {
        val status = CoreMigrations(openExistingDb()).status()
        val data = DbStatusJson(
            path = absoluteDbPath.toString(),
            currentVersion = status.currentVersion,
            pendingCount = status.pendingCount,
            migrations = status.migrations.map(MigrationJson::of),
        )
        respond(DbStatusJson.serializer(), data) {
            buildString {
                appendLine("core.db: ${it.path}")
                appendLine("現在のバージョン: ${it.currentVersion ?: "なし"}（未適用 ${it.pendingCount} 件）")
                append(status.migrations.joinToString("\n", transform = ::humanRow))
            }.trimEnd()
        }
    }
}

class DbResetCommand(io: CliIo) : DbCommand("reset", "core.db の中身を全て消し、マイグレーションを最初から適用し直す", io) {
    private val yes by option("--yes", help = "全てのデータを消すことを確認した").flag()

    override fun execute() {
        if (!yes) throw CliFailure(ErrorCode.USAGE, "db reset は全てのデータを消します。実行するには --yes を付けてください")
        val result = CoreMigrations(openExistingDb()).reset()
        respond(DbMigrateJson.serializer(), DbMigrateJson.of(absoluteDbPath, result)) {
            "core.db: ${it.path}\n中身を消して ${it.appliedCount} 件を適用し直しました（現在のバージョン: ${it.currentVersion ?: "なし"}）"
        }
    }
}

private fun humanRow(row: MigrationRow): String {
    val state = when (row.state) {
        MigrationState.APPLIED -> "適用済み"
        MigrationState.PENDING -> "未適用"
        MigrationState.FAILED -> "失敗"
        MigrationState.OTHER -> "その他"
    }
    return "  ${(row.version ?: "-").padEnd(4)} ${row.description}  $state  ${row.installedAt?.let(Jst::format) ?: ""}".trimEnd()
}

@Serializable
data class MigrationJson(
    val version: String?,
    val description: String,
    /** applied / pending / failed / other */
    val state: String,
    val installedAt: String?,
) {
    companion object {
        fun of(row: MigrationRow) = MigrationJson(
            version = row.version,
            description = row.description,
            state = row.state.name.lowercase(),
            installedAt = row.installedAt?.let(UtcTimestamps::format),
        )
    }
}

@Serializable
data class DbStatusJson(
    val path: String,
    val currentVersion: String?,
    val pendingCount: Int,
    val migrations: List<MigrationJson>,
)

@Serializable
data class DbMigrateJson(
    val path: String,
    val appliedCount: Int,
    val applied: List<MigrationJson>,
    val currentVersion: String?,
) {
    companion object {
        fun of(path: Path, result: MigrateResult) = DbMigrateJson(
            path = path.toString(),
            appliedCount = result.appliedCount,
            applied = result.applied.map(MigrationJson::of),
            currentVersion = result.currentVersion,
        )
    }
}
