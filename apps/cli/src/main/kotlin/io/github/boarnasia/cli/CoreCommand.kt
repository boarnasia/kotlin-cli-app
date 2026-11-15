package io.github.boarnasia.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import io.github.boarnasia.core.db.Actor
import io.github.boarnasia.core.db.CoreDb
import io.github.boarnasia.core.db.id.SnowflakeIdGenerator
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.KSerializer

/** 実際に処理をするコマンド（db status など）の共通部分。 */
abstract class CoreCommand(name: String, private val summary: String, protected val io: CliIo) : CliktCommand(name) {

    val json by option("--json", help = "結果を JSON で出す（形は docs/json-contract.md）").flag()

    override fun help(context: Context): String = summary

    final override fun run() {
        io.command = currentContext.commandNameWithParents().drop(1).joinToString(" ")
        execute()
    }

    protected abstract fun execute()

    protected fun <T> respond(serializer: KSerializer<T>, data: T, human: (T) -> String) {
        io.out.println(if (json) CliJson.success(io.command, serializer, data) else human(data))
    }
}

/** core.db を開くコマンド。 */
abstract class DbCommand(name: String, summary: String, io: CliIo) : CoreCommand(name, summary, io) {

    val dbPath: Path by option("--db", envvar = ENV_DB_PATH, help = "core.db のパス（環境変数 $ENV_DB_PATH でも指定できる）")
        .path()
        .required()

    protected val absoluteDbPath: Path get() = dbPath.toAbsolutePath().normalize()

    // 存在しないパスを SQLite に渡すと空の DB ができてしまうので、migrate 以外は先に確かめる
    protected fun openExistingDb(): CoreDb {
        if (!Files.isRegularFile(absoluteDbPath)) {
            throw CliFailure(ErrorCode.DB_NOT_FOUND, "core.db がありません: $absoluteDbPath（先に db migrate を実行してください）")
        }
        return CoreDb.openFile(absoluteDbPath)
    }

    protected fun openOrCreateDb(): CoreDb {
        val parent = absoluteDbPath.parent
        if (parent != null && !Files.isDirectory(parent)) {
            throw CliFailure(ErrorCode.DB_NOT_FOUND, "core.db を置くディレクトリがありません: $parent")
        }
        return CoreDb.openFile(absoluteDbPath)
    }

    companion object {
        const val ENV_DB_PATH = "CORE_DB_PATH"
    }
}

/** core.db のデータを書き換えるコマンド。操作した管理者を --actor で必ず受け取る。 */
abstract class WriteCommand(name: String, summary: String, io: CliIo) : DbCommand(name, summary, io) {

    // 管理者の表ができるまでは内部 ID だけを受け取り、いるかどうかは確かめない（S- の番号での指定は staff モジュールで足す）
    private val actorId: Long by option("--actor", help = "操作した管理者の内部 ID")
        .long()
        .required()
        .validate { require(it > 0) { "--actor には管理者の内部 ID（正の整数）を指定してください" } }

    protected val actor: Actor.Staff get() = Actor.Staff(actorId)

    /** 内部 ID を作る。CORE_APP_ID は書き込みのときだけ要るので、ここで初めて読む。 */
    protected fun idGenerator(): SnowflakeIdGenerator {
        val raw = io.env[ENV_APP_ID]
        val appId = raw?.toIntOrNull()?.takeIf { it in 0..SnowflakeIdGenerator.MAX_APP_ID }
            ?: throw CliFailure(
                ErrorCode.USAGE,
                "$ENV_APP_ID に 0〜${SnowflakeIdGenerator.MAX_APP_ID} の app-id を指定してください（CLI は 3）。今の値: ${raw ?: "なし"}",
            )
        return SnowflakeIdGenerator(appId)
    }

    companion object {
        const val ENV_APP_ID = "CORE_APP_ID"
    }
}

/** パスワードを読む。シェルの履歴やプロセスの一覧に残らないよう、引数の値では受け取らない。 */
object PasswordInput {
    fun read(io: CliIo, fromStdin: Boolean, confirm: Boolean): String {
        if (fromStdin) {
            return io.input.bufferedReader(Charsets.UTF_8).readLine()
                ?: throw CliFailure(ErrorCode.USAGE, "標準入力にパスワードがありません")
        }
        val first = io.console.readPassword("パスワード: ")
            ?: throw CliFailure(ErrorCode.USAGE, "端末が無いので、--password-stdin を付けて標準入力からパスワードを渡してください")
        if (confirm && io.console.readPassword("パスワード（確認）: ") != first) {
            throw CliFailure(ErrorCode.VALIDATION, "確認のパスワードが一致しません")
        }
        return first
    }
}

/** サブコマンドをまとめるだけのコマンド（db など）。 */
class GroupCommand(name: String, private val summary: String) : CliktCommand(name) {
    override fun help(context: Context): String = summary

    override fun run() = Unit
}
