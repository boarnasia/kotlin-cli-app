package io.github.boarnasia.cli

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DbCommandsTest {

    @TempDir
    lateinit var dir: Path

    private val db get() = dir.resolve("core.db").toString()

    @Test
    fun `db migrate は DB を作って適用し、JSON の決めた形で返す`() {
        val result = cli("db", "migrate", "--db", db, "--json")

        result.exitCode shouldBe 0
        val json = result.json
        json["contractVersion"] shouldBe JsonPrimitive(1)
        json["ok"] shouldBe JsonPrimitive(true)
        json["command"] shouldBe JsonPrimitive("db migrate")
        val data = json["data"]!!.jsonObject
        data.keys shouldBe setOf("path", "appliedCount", "applied", "currentVersion")
        data["appliedCount"]!!.jsonPrimitive.int shouldBe 1
        data["currentVersion"] shouldBe JsonPrimitive("1")
        val applied = data["applied"]!!.jsonArray.single().jsonObject
        applied["state"] shouldBe JsonPrimitive("applied")
        applied["installedAt"]!!.jsonPrimitive.content shouldMatch Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z""")
    }

    @Test
    fun `db status は適用後に未適用 0 件を返す`() {
        cli("db", "migrate", "--db", db)

        val result = cli("db", "status", "--db", db, "--json")

        result.exitCode shouldBe 0
        val data = result.json["data"]!!.jsonObject
        data.keys shouldBe setOf("path", "currentVersion", "pendingCount", "migrations")
        data["pendingCount"]!!.jsonPrimitive.int shouldBe 0
        data["migrations"]!!.jsonArray.single().jsonObject["version"] shouldBe JsonPrimitive("1")
    }

    @Test
    fun `DB のパスは環境変数でも指定できる`() {
        val result = cli("db", "migrate", "--json", env = mapOf("CORE_DB_PATH" to db))

        result.exitCode shouldBe 0
        Files.exists(Path.of(db)) shouldBe true
    }

    @Test
    fun `DB が無ければ status は作らずに DB_NOT_FOUND で終わる`() {
        val result = cli("db", "status", "--db", db, "--json")

        result.exitCode shouldBe 3
        result.json["ok"] shouldBe JsonPrimitive(false)
        result.json["command"] shouldBe JsonPrimitive("db status")
        result.json["error"]!!.jsonObject["code"] shouldBe JsonPrimitive("DB_NOT_FOUND")
        Files.exists(Path.of(db)) shouldBe false
    }

    @Test
    fun `--db が無いときは --json でも USAGE の JSON を返す`() {
        val result = cli("db", "status", "--json")

        result.exitCode shouldBe 2
        result.json["error"]!!.jsonObject["code"] shouldBe JsonPrimitive("USAGE")
        result.json["command"] shouldBe JsonPrimitive("db status")
        result.json["error"]!!.jsonObject["message"]!!.jsonPrimitive.content shouldContain "--db"
    }

    @Test
    fun `知らないコマンドは USAGE で終わる`() {
        val result = cli("db", "nope", "--json")

        result.exitCode shouldBe 2
        result.json["error"]!!.jsonObject["code"] shouldBe JsonPrimitive("USAGE")
    }

    @Test
    fun `db reset は --yes が無ければ何もしない`() {
        cli("db", "migrate", "--db", db)

        val refused = cli("db", "reset", "--db", db, "--json")
        refused.exitCode shouldBe 2

        val done = cli("db", "reset", "--db", db, "--yes", "--json")
        done.exitCode shouldBe 0
        done.json["data"]!!.jsonObject["appliedCount"]!!.jsonPrimitive.int shouldBe 1
    }

    @Test
    fun `人向けの表示は時刻を JST で出し、エラーは標準エラーに出す`() {
        cli("db", "migrate", "--db", db)

        val status = cli("db", "status", "--db", db)
        status.out shouldContain "未適用 0 件"
        status.out shouldContain "JST"

        val missing = cli("db", "status", "--db", dir.resolve("none.db").toString())
        missing.exitCode shouldBe 3
        missing.out shouldBe ""
        missing.err shouldContain "core.db がありません"
    }

    @Test
    fun `成功の JSON でも値の無い項目は null で出す`() {
        val result = cli("db", "status", "--db", db.also { cli("db", "migrate", "--db", it) }, "--json")

        result.json["data"]!!.jsonObject["migrations"]!!.jsonArray.single().jsonObject.keys shouldBe
            setOf("version", "description", "state", "installedAt")
        Json.parseToJsonElement(CliJson.failure(null, ErrorCode.INTERNAL, "x")).jsonObject["command"] shouldBe JsonNull
    }
}
