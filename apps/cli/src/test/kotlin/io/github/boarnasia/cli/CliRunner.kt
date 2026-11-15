package io.github.boarnasia.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

data class CliResult(val exitCode: Int, val out: String, val err: String) {
    val json: JsonObject get() = Json.parseToJsonElement(out).jsonObject
}

fun cli(
    vararg args: String,
    env: Map<String, String> = emptyMap(),
    stdin: String = "",
    console: PasswordConsole = PasswordConsole.NONE,
): CliResult {
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()
    val io = CliIo(
        out = PrintStream(out, true, Charsets.UTF_8),
        err = PrintStream(err, true, Charsets.UTF_8),
        input = stdin.byteInputStream(Charsets.UTF_8),
        console = console,
        env = env,
    )
    val code = runCli(args.toList(), io)
    return CliResult(code, out.toString(Charsets.UTF_8), err.toString(Charsets.UTF_8))
}
