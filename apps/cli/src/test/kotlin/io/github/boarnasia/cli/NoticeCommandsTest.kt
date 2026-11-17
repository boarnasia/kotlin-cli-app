package io.github.boarnasia.cli

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NoticeCommandsTest {
    @TempDir lateinit var dir: Path
    private lateinit var env: Map<String, String>
    @BeforeEach fun setup() {
        env = mapOf("CORE_DB_PATH" to dir.resolve("core.db").toString(), "CORE_APP_ID" to "3")
        cli("db", "migrate", env = env).exitCode shouldBe 0
        cli("staff", "bootstrap", "--email", "admin@example.com", "--name", "管理者", "--password-stdin", env = env, stdin = "correct horse\n").exitCode shouldBe 0
    }
    private fun run(vararg args: String, actor: String = "S-000001") = cli("notice", *args, "--actor", actor, "--json", env = env)
    private val CliResult.data get() = json["data"]!!.jsonObject
    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content
    private fun create(vararg extra: String): JsonObject {
        val result = run("create", "--title", "お休み", "--body", "年末年始", *extra)
        result.exitCode shouldBe 0
        return result.data
    }
    @Test fun `下書きは契約の全項目と null の日時を出し番号と ID で取得できる`() {
        val n = create()
        n.keys shouldBe setOf("id", "displayNumber", "title", "body", "status", "publishStartAt", "publishEndAt", "deletedAt", "createdAt", "updatedAt", "createdBy", "updatedBy")
        n.str("displayNumber") shouldBe "N-000001"
        n.str("status") shouldBe "draft"
        n["publishStartAt"] shouldBe JsonNull
        n["publishEndAt"] shouldBe JsonNull
        n["deletedAt"] shouldBe JsonNull
        n["createdBy"]!!.jsonObject.str("type") shouldBe "staff"
        run("get", n.str("id")).data shouldBe n
        run("get", "N-000001").data shouldBe n
        run("get", "N-000009").exitCode shouldBe 3
        run("get", "I-000001").exitCode shouldBe 5
        run("get", "6f1c2d3e-4a5b-4c6d-8e9f-0a1b2c3d4e5f").exitCode shouldBe 5
        run("get", "abc").exitCode shouldBe 2
    }
    @Test fun `日時は JST と時差を UTC に変換し日付だけと誤った日時は USAGE`() {
        listOf("2099-12-01T09:00", "2099-12-01T09:00+09:00", "2099-12-01T00:00:00Z").forEach {
            create("--start", it).str("publishStartAt") shouldBe "2099-12-01T00:00:00.000Z"
        }
        listOf("2099-12-01", "bad", "2099-02-30T09:00").forEach {
            run("create", "--title", "題名", "--body", "本文", "--start", it).exitCode shouldBe 2
        }
        run("visible", "--at", "bad").exitCode shouldBe 2
        run("create", "--title", "題名", "--body", "本文", "--end", "bad").exitCode shouldBe 2
    }
    @Test fun `予約の変更と日時の消去と空の変更と同時指定を検査する`() {
        create()
        run("update", "N-000001", "--start", "2099-12-01T09:00", "--end", "2099-12-02T09:00", "--title", "変更", "--body", "本文変更").let {
            it.exitCode shouldBe 0
            it.data.str("status") shouldBe "scheduled"
            it.data.str("title") shouldBe "変更"
            it.data.str("body") shouldBe "本文変更"
        }
        run("update", "N-000001", "--clear-start", "--clear-end").data.str("status") shouldBe "draft"
        run("update", "N-000001").exitCode shouldBe 5
        run("update", "N-000001", "--start", "2099-12-01T09:00", "--clear-start").exitCode shouldBe 2
        run("update", "N-000001", "--end", "2099-12-01T09:00", "--clear-end").exitCode shouldBe 2
        run("update", "N-000001", "--title", " ").exitCode shouldBe 5
    }
    @Test fun `日時の規則違反は VALIDATION で公開中の開始変更と下書きの終了は CONFLICT`() {
        run("create", "--title", "題名", "--body", "本文", "--end", "2099-12-01T09:00").exitCode shouldBe 5
        run("create", "--title", "題名", "--body", "本文", "--start", "2099-12-02T09:00", "--end", "2099-12-01T09:00").exitCode shouldBe 5
        run("create", "--title", "題名", "--body", "本文", "--start", "2000-01-01T09:00", "--end", "2001-01-01T09:00").exitCode shouldBe 5
        run("create", "--title", "題名", "--body", "本文", "--start", "2099-12-01T09:00", "--end", "2099-12-01T09:00").exitCode shouldBe 5
        create()
        run("update", "N-000001", "--start", "2099-12-01T09:00", "--end", "2099-12-01T09:00").exitCode shouldBe 5
        run("get", "N-000001").data.str("status") shouldBe "draft"
        run("end", "N-000001").exitCode shouldBe 4
        run("update", "N-000001", "--start", "2000-01-01T09:00").data.str("status") shouldBe "active"
        run("update", "N-000001", "--clear-start").exitCode shouldBe 4
        run("update", "N-000001", "--title", "訂正", "--body", "訂正本文", "--end", "2099-12-01T09:00").exitCode shouldBe 0
        run("end", "N-000001").data.str("status") shouldBe "ended"
        run("update", "N-000001", "--title", "変更").exitCode shouldBe 4
        run("end", "N-000001").exitCode shouldBe 4
    }
    @Test fun `公開一覧は at の境界で切り替わり開始の新しい順に出す`() {
        create("--start", "2099-12-01T09:00", "--end", "2099-12-02T09:00")
        create("--start", "2099-12-01T10:00")
        fun visible(at: String) = run("visible", "--at", at).data["items"]!!.jsonArray.map { it.jsonObject.str("displayNumber") }
        visible("2099-12-01T08:59") shouldBe emptyList()
        visible("2099-12-01T09:00") shouldBe listOf("N-000001")
        visible("2099-12-01T10:00") shouldBe listOf("N-000002", "N-000001")
        visible("2099-12-02T09:00") shouldBe listOf("N-000002")
        val result = run("visible", "--at", "2099-12-01T09:00").data
        result.keys shouldBe setOf("at", "items")
        result.str("at") shouldBe "2099-12-01T00:00:00.000Z"
        result["items"]!!.jsonArray.single().jsonObject.str("status") shouldBe "scheduled"
    }
    @Test fun `削除は yes が必須で公開中も直接削除でき一覧は既定で除く`() {
        create("--start", "2000-01-01T09:00")
        create()
        run("delete", "N-000001").exitCode shouldBe 2
        val deleted = run("delete", "N-000001", "--yes")
        deleted.exitCode shouldBe 0
        deleted.data["deletedAt"]!!.jsonPrimitive.content.isNotEmpty() shouldBe true
        run("get", "N-000001").data shouldBe deleted.data
        run("delete", "N-000001", "--yes").exitCode shouldBe 4
        run("list").data["items"]!!.jsonArray.map { it.jsonObject.str("displayNumber") } shouldBe listOf("N-000002")
        run("list", "--status", "deleted").data["total"] shouldBe JsonPrimitive(1)
        run("visible").data["items"]!!.jsonArray.size shouldBe 0
    }
    @Test fun `一覧は状態とページと番号の新しい順で件数制限も検査する`() {
        create()
        create("--start", "2099-12-01T09:00")
        val page = run("list", "--limit", "1", "--offset", "1").data
        page.keys shouldBe setOf("items", "total", "limit", "offset")
        page["total"] shouldBe JsonPrimitive(2)
        page["items"]!!.jsonArray.single().jsonObject.str("displayNumber") shouldBe "N-000001"
        run("list", "--status", "scheduled").data["items"]!!.jsonArray.single().jsonObject.str("displayNumber") shouldBe "N-000002"
        run("list", "--status", "bad").exitCode shouldBe 2
        run("list", "--limit", "501").exitCode shouldBe 5
        run("list", "--offset", "-1").exitCode shouldBe 5
    }
    @Test fun `権限の無い管理者は閲覧でき全書き込みは FORBIDDEN で停止中は閲覧も拒む`() {
        create("--start", "2000-01-01T09:00")
        cli("staff", "create", "--email", "viewer@example.com", "--name", "閲覧者", "--password-stdin", "--actor", "S-000001", env = env, stdin = "correct horse\n").exitCode shouldBe 0
        listOf(arrayOf("get", "N-000001"), arrayOf("list"), arrayOf("visible")).forEach { run(*it, actor = "S-000002").exitCode shouldBe 0 }
        listOf(arrayOf("create", "--title", "題名", "--body", "本文"), arrayOf("update", "N-000001", "--title", "変更"), arrayOf("end", "N-000001"), arrayOf("delete", "N-000001", "--yes")).forEach {
            val result = run(*it, actor = "S-000002")
            result.exitCode shouldBe 7
            result.json["error"]!!.jsonObject.str("code") shouldBe "FORBIDDEN"
        }
        run("get", "N-000001").data.str("status") shouldBe "active"
        cli("staff", "suspend", "S-000002", "--actor", "S-000001", env = env).exitCode shouldBe 0
        run("list", actor = "S-000002").exitCode shouldBe 7
    }
    @Test fun `人向けは一行の期間と JST と本文と操作した人の番号を出す`() {
        create("--start", "2099-12-01T09:00", "--end", "2099-12-02T09:00")
        val result = cli("notice", "list", "--actor", "S-000001", env = env)
        result.exitCode shouldBe 0
        result.out.trim().lines().size shouldBe 2
        result.out shouldContain "N-000001  予約"
        result.out shouldContain "2099-12-01"
        result.out shouldContain "JST"
        result.out shouldContain "お休み"
        val one = cli("notice", "get", "N-000001", "--actor", "S-000001", env = env).out
        one shouldContain "年末年始"
        one shouldContain "管理者 S-000001"
        cli("notice", "visible", "--at", "2099-12-01T09:00", "--actor", "S-000001", env = env).out shouldContain "N-000001"
    }
}
