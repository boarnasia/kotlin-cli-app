package io.github.boarnasia.cli

import io.github.boarnasia.core.db.CoreErrorKind
import java.io.InputStream
import java.io.PrintStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * --json の失敗の種類と終了コード。dashboard が読む契約なので、code の文字列と終了コードは変えない（docs/json-contract.md）。
 */
enum class ErrorCode(val exitCode: Int) {
    INTERNAL(1),
    USAGE(2),
    NOT_FOUND(3),
    DB_NOT_FOUND(3),
    CONFLICT(4),
    VALIDATION(5),
    AUTH_FAILED(6),
}

class CliFailure(val code: ErrorCode, override val message: String) : RuntimeException(message)

fun CoreErrorKind.toErrorCode(): ErrorCode = when (this) {
    CoreErrorKind.NOT_FOUND -> ErrorCode.NOT_FOUND
    CoreErrorKind.CONFLICT -> ErrorCode.CONFLICT
    CoreErrorKind.VALIDATION -> ErrorCode.VALIDATION
    CoreErrorKind.AUTH_FAILED -> ErrorCode.AUTH_FAILED
}

/** 表示しない入力でパスワードを読む。端末が無ければ null。 */
fun interface PasswordConsole {
    fun readPassword(prompt: String): String?

    companion object {
        val NONE = PasswordConsole { null }

        // JDK 22 以降は端末でなくても Console が返るので、端末かどうかを別に確かめる
        val SYSTEM = PasswordConsole { prompt ->
            System.console()?.takeIf { it.isTerminal }?.readPassword(prompt)?.let(::String)
        }
    }
}

class CliIo(
    val out: PrintStream,
    val err: PrintStream,
    val input: InputStream = InputStream.nullInputStream(),
    val console: PasswordConsole = PasswordConsole.NONE,
    /** .env と環境変数を合わせたもの。 */
    val env: Map<String, String> = emptyMap(),
) {
    /** 実行中のコマンド（例 "db status"）。失敗を --json で返すときに入れる。 */
    var command: String? = null
}

object CliJson {
    const val CONTRACT_VERSION = 1

    private val json = Json { encodeDefaults = true }

    fun <T> success(command: String?, serializer: KSerializer<T>, data: T): String = json.encodeToString(
        buildJsonObject {
            put("contractVersion", CONTRACT_VERSION)
            put("ok", true)
            put("command", command?.let(::JsonPrimitive) ?: JsonNull)
            put("data", json.encodeToJsonElement(serializer, data))
        },
    )

    fun failure(command: String?, code: ErrorCode, message: String): String = json.encodeToString(
        buildJsonObject {
            put("contractVersion", CONTRACT_VERSION)
            put("ok", false)
            put("command", command?.let(::JsonPrimitive) ?: JsonNull)
            put(
                "error",
                buildJsonObject {
                    put("code", code.name)
                    put("message", message)
                },
            )
        },
    )
}

/** 人向けの表示だけ JST にする。--json と core.db は UTC。 */
object Jst {
    private val ZONE = ZoneId.of("Asia/Tokyo")
    private val FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss 'JST'").withZone(ZONE)

    fun format(instant: Instant): String = FORMATTER.format(instant)
}
