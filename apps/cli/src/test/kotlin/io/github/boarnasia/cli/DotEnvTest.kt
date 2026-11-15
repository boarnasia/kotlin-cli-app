package io.github.boarnasia.cli

import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DotEnvTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun `コメントと空行を飛ばし、引用符を外す`() {
        DotEnv.parse(
            listOf("# コメント", "", "CORE_APP_ID=3", "export CORE_DB_PATH=\"./data/core.db\"", "NAME='a=b'"),
        ) shouldBe mapOf("CORE_APP_ID" to "3", "CORE_DB_PATH" to "./data/core.db", "NAME" to "a=b")
    }

    @Test
    fun `環境変数を dotenv のファイルより優先する`() {
        val file = dir.resolve(".env").also { Files.writeString(it, "CORE_APP_ID=3\nCORE_DB_PATH=a.db\n") }

        DotEnv.load(file, mapOf("CORE_DB_PATH" to "b.db")) shouldBe mapOf("CORE_APP_ID" to "3", "CORE_DB_PATH" to "b.db")
    }

    @Test
    fun `dotenv のファイルが無ければ環境変数だけを使う`() {
        DotEnv.load(dir.resolve(".env"), mapOf("X" to "1")) shouldBe mapOf("X" to "1")
    }
}
