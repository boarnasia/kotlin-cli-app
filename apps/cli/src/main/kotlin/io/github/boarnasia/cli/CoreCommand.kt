package io.github.boarnasia.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import io.github.boarnasia.core.db.CoreDb
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

/** サブコマンドをまとめるだけのコマンド（db など）。 */
class GroupCommand(name: String, private val summary: String) : CliktCommand(name) {
    override fun help(context: Context): String = summary

    override fun run() = Unit
}
