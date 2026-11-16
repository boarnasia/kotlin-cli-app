package io.github.boarnasia.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.UsageError
import io.github.boarnasia.cli.db.dbCommand
import io.github.boarnasia.cli.staff.staffCommand
import io.github.boarnasia.cli.user.userCommand
import io.github.boarnasia.core.db.CoreException
import java.nio.file.Path
import kotlin.system.exitProcess

const val PROGRAM_NAME = "core-cli"

fun main(args: Array<String>) {
    val env = DotEnv.load(Path.of(".env"), System.getenv())
    exitProcess(runCli(args.toList(), CliIo(System.out, System.err, System.`in`, PasswordConsole.SYSTEM, env)))
}

private class RootCommand : CliktCommand(PROGRAM_NAME) {
    override fun help(context: Context): String = "core.db を操作する管理用の CLI"

    override fun run() = Unit
}

/** 終了コードを返す。テストから入出力と環境変数を差し替えて呼べるよう、main と分ける。 */
fun runCli(args: List<String>, io: CliIo): Int {
    // jOOQ は初回の実行時にロゴを出すので、--json の出力に混ざらないよう止める
    System.setProperty("org.jooq.no-logo", "true")
    System.setProperty("org.jooq.no-tips", "true")

    // 引数の解析に失敗したときも、--json を付けていれば失敗を JSON で返す
    val wantsJson = "--json" in args
    val root = RootCommand()
        .subcommands(dbCommand(io), staffCommand(io), userCommand(io))
        .context { readEnvvar = { io.env[it] } }

    fun fail(code: ErrorCode, message: String): Int {
        if (wantsJson) io.out.println(CliJson.failure(io.command, code, message)) else io.err.println("エラー: $message")
        return code.exitCode
    }

    return try {
        root.parse(args)
        0
    } catch (e: CliFailure) {
        fail(e.code, e.message)
    } catch (e: CoreException) {
        fail(e.kind.toErrorCode(), e.message ?: e.kind.name)
    } catch (e: PrintHelpMessage) {
        io.out.println(root.getFormattedHelp(e))
        if (e.error) ErrorCode.USAGE.exitCode else 0
    } catch (e: PrintMessage) {
        io.out.println(e.message)
        e.statusCode
    } catch (e: CliktError) {
        if (wantsJson) {
            io.command = (e as? UsageError)?.context?.commandNameWithParents()?.drop(1)?.joinToString(" ")?.ifEmpty { null }
            // UsageError の message は空のことがあり、Clikt が整形した最後の行（"Error: ..."）に理由が入る
            val reason = root.getFormattedHelp(e)?.lines()?.lastOrNull { it.isNotBlank() }?.removePrefix("Error: ")
            fail(ErrorCode.USAGE, reason ?: e.message ?: "引数が正しくありません")
        } else {
            io.err.println(root.getFormattedHelp(e))
            ErrorCode.USAGE.exitCode
        }
    } catch (e: Exception) {
        fail(ErrorCode.INTERNAL, "${e::class.simpleName}: ${e.message}")
    }
}
