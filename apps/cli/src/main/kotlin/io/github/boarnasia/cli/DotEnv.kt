package io.github.boarnasia.cli

import java.nio.file.Files
import java.nio.file.Path

/** .env を読む。同じ名前の環境変数があればそちらを優先する。 */
object DotEnv {
    fun load(file: Path, environment: Map<String, String>): Map<String, String> {
        val fromFile = if (Files.isRegularFile(file)) parse(Files.readAllLines(file)) else emptyMap()
        return fromFile + environment
    }

    fun parse(lines: List<String>): Map<String, String> = lines
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
        .associate { line ->
            val (key, value) = line.removePrefix("export ").split('=', limit = 2)
            key.trim() to value.trim().unquote()
        }

    private fun String.unquote(): String =
        if (length >= 2 && first() == last() && (first() == '"' || first() == '\'')) substring(1, length - 1) else this
}
