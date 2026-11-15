package io.github.boarnasia.cli

import io.github.boarnasia.core.db.CoreDb
import io.github.boarnasia.core.db.id.SnowflakeIdGenerator
import io.github.boarnasia.core.password.Argon2idPasswordHasher
import io.github.boarnasia.core.user.Registrant
import io.github.boarnasia.core.user.UserService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import java.nio.file.Path
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class UserCommandsTest {

    @TempDir
    lateinit var dir: Path

    private lateinit var env: Map<String, String>

    @BeforeEach
    fun setUp() {
        env = mapOf("CORE_DB_PATH" to dir.resolve("core.db").toString(), "CORE_APP_ID" to "3")
        cli("db", "migrate", env = env).exitCode shouldBe 0
    }

    private fun run(vararg args: String, stdin: String = "", console: PasswordConsole = PasswordConsole.NONE, env: Map<String, String> = this.env) =
        cli(*args, env = env, stdin = stdin, console = console)

    private fun create(email: String = "taro@example.com", name: String = "山田 太郎", password: String = "correct horse", actor: String = "7"): JsonObject {
        val result = run("user", "create", "--email", email, "--name", name, "--password-stdin", "--actor", actor, "--json", stdin = "$password\n")
        result.exitCode shouldBe 0
        return result.json["data"]!!.jsonObject
    }

    private fun CliResult.errorCode() = json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    private fun actor(type: String, id: String) = buildJsonObject {
        put("type", type)
        put("id", id)
    }

    @Test
    fun `user create は会員を登録し、決めた 12 項目で出す`() {
        val user = create()

        user.keys shouldBe setOf(
            "id", "displayNumber", "publicId", "email", "name", "status", "suspendedAt", "suspensionReason",
            "createdAt", "updatedAt", "createdBy", "updatedBy",
        )
        user["displayNumber"] shouldBe JsonPrimitive("M-000001")
        user["status"] shouldBe JsonPrimitive("active")
        user["email"] shouldBe JsonPrimitive("taro@example.com")
        user["suspendedAt"] shouldBe JsonNull
        user["suspensionReason"] shouldBe JsonNull
        user["createdBy"] shouldBe actor("staff", "7")
        user["updatedBy"] shouldBe actor("staff", "7")
        (user["id"] as JsonPrimitive).isString shouldBe true
        SnowflakeIdGenerator.decompose(user["id"]!!.jsonPrimitive.content.toLong()).appId shouldBe 3
    }

    @Test
    fun `本人が登録した会員は createdBy が member になる`() {
        val db = CoreDb.openFile(dir.resolve("core.db"))
        val member = UserService(db, SnowflakeIdGenerator(1), Argon2idPasswordHasher()).register("self@example.com", "本人", "correct horse", Registrant.Self)

        val user = run("user", "get", member.id.toString(), "--json").json["data"]!!.jsonObject

        user["createdBy"] shouldBe actor("member", member.id.toString())
    }

    @Test
    fun `出力にパスワードもハッシュも含めない`() {
        val outputs = listOf(
            run("user", "create", "--email", "a@example.com", "--name", "A", "--password-stdin", "--actor", "1", "--json", stdin = "correct horse\n"),
            run("user", "set-password", "M-000001", "--password-stdin", "--actor", "1", "--json", stdin = "battery staple\n"),
            run("user", "authenticate", "--email", "a@example.com", "--password-stdin", "--json", stdin = "battery staple\n"),
        )

        outputs.forEach {
            it.exitCode shouldBe 0
            it.out shouldNotContain "argon2"
            it.out shouldNotContain "correct horse"
            it.out shouldNotContain "battery staple"
            it.json["data"].toString().lowercase() shouldNotContain "password"
        }
    }

    @Test
    fun `書き込みに --actor が無いか正の整数でなければ USAGE で、何も書き込まない`() {
        val missing = run("user", "create", "--email", "a@example.com", "--name", "A", "--password-stdin", "--json", stdin = "correct horse\n")
        missing.exitCode shouldBe 2
        missing.errorCode() shouldBe "USAGE"

        create()
        listOf("S-000001", "0", "-1", "x").forEach {
            run("user", "suspend", "M-000001", "--actor", it, "--json").exitCode shouldBe 2
        }
        run("user", "get", "M-000001", "--json").json["data"]!!.jsonObject["status"] shouldBe JsonPrimitive("active")
        run("user", "list", "--json").json["data"]!!.jsonObject["total"]!!.jsonPrimitive.int shouldBe 1
    }

    @Test
    fun `読むだけのコマンドは --actor を取らない`() {
        create()

        run("user", "get", "M-000001", "--actor", "1", "--json").exitCode shouldBe 2
        run("user", "list", "--actor", "1", "--json").exitCode shouldBe 2
    }

    @Test
    fun `CORE_APP_ID が無いか範囲の外なら user create は USAGE で、読むだけのコマンドは動く`() {
        listOf(env - "CORE_APP_ID", env + ("CORE_APP_ID" to "32"), env + ("CORE_APP_ID" to "x")).forEach { e ->
            val result = run("user", "create", "--email", "a@example.com", "--name", "A", "--password-stdin", "--actor", "1", "--json", stdin = "correct horse\n", env = e)
            result.exitCode shouldBe 2
            result.json["error"]!!.jsonObject["message"]!!.jsonPrimitive.content shouldContain "CORE_APP_ID"
        }
        run("user", "list", "--json", env = env - "CORE_APP_ID").exitCode shouldBe 0
    }

    @Test
    fun `会員は 3 種類の ID のどれでも指定でき、形・種類・有無の誤りを終了コードで分ける`() {
        val user = create()

        listOf(user["id"], user["displayNumber"], user["publicId"]).forEach {
            run("user", "get", it!!.jsonPrimitive.content, "--json").json["data"] shouldBe user
        }
        run("user", "get", "abc", "--json").let { it.exitCode shouldBe 2; it.errorCode() shouldBe "USAGE" }
        run("user", "get", "I-000001", "--json").let { it.exitCode shouldBe 5; it.errorCode() shouldBe "VALIDATION" }
        run("user", "get", "M-999999", "--json").let { it.exitCode shouldBe 3; it.errorCode() shouldBe "NOT_FOUND" }
    }

    @Test
    fun `メールアドレスの重複は CONFLICT、値の誤りは VALIDATION`() {
        create(email = "taro@example.com")

        run("user", "create", "--email", "TARO@example.com", "--name", "B", "--password-stdin", "--actor", "1", "--json", stdin = "correct horse\n")
            .let { it.exitCode shouldBe 4; it.errorCode() shouldBe "CONFLICT" }
        run("user", "create", "--email", "bad", "--name", "B", "--password-stdin", "--actor", "1", "--json", stdin = "correct horse\n")
            .let { it.exitCode shouldBe 5; it.errorCode() shouldBe "VALIDATION" }
        run("user", "create", "--email", "b@example.com", "--name", "B", "--password-stdin", "--actor", "1", "--json", stdin = "short\n")
            .let { it.exitCode shouldBe 5; it.errorCode() shouldBe "VALIDATION" }
    }

    @Test
    fun `パスワードは標準入力の 1 行目を使い、末尾の改行だけを除く`() {
        create(password = "  spaced pass  ")

        run("user", "authenticate", "--email", "taro@example.com", "--password-stdin", "--json", stdin = "  spaced pass  \r\nnext line\n").exitCode shouldBe 0
        run("user", "authenticate", "--email", "taro@example.com", "--password-stdin", "--json", stdin = "spaced pass\n").exitCode shouldBe 6
    }

    @Test
    fun `端末も --password-stdin も無ければ USAGE で、会員は作られない`() {
        val result = run("user", "create", "--email", "a@example.com", "--name", "A", "--actor", "1", "--json")

        result.exitCode shouldBe 2
        run("user", "list", "--json").json["data"]!!.jsonObject["total"]!!.jsonPrimitive.int shouldBe 0
    }

    @Test
    fun `端末では表示しない入力を 2 回求め、一致しなければ登録しない`() {
        val answers = ArrayDeque(listOf("correct horse", "correct horse", "correct horse", "different one"))
        val prompts = mutableListOf<String>()
        val console = PasswordConsole { prompts += it; answers.removeFirst() }

        run("user", "create", "--email", "a@example.com", "--name", "A", "--actor", "1", "--json", console = console).exitCode shouldBe 0
        prompts.size shouldBe 2
        run("user", "create", "--email", "b@example.com", "--name", "B", "--actor", "1", "--json", console = console).exitCode shouldBe 5
        run("user", "list", "--json").json["data"]!!.jsonObject["total"]!!.jsonPrimitive.int shouldBe 1
    }

    @Test
    fun `user list は絞り込み・件数・読み飛ばし・全件数を返す`() {
        (1..5).forEach { create(email = "m$it@example.com", name = "会員$it") }
        run("user", "suspend", "M-000002", "--actor", "1").exitCode shouldBe 0

        val page = run("user", "list", "--limit", "2", "--offset", "2", "--json").json["data"]!!.jsonObject
        page.keys shouldBe setOf("items", "total", "limit", "offset")
        page["items"]!!.jsonArray.map { it.jsonObject["displayNumber"]!!.jsonPrimitive.content } shouldBe listOf("M-000003", "M-000004")
        page["total"]!!.jsonPrimitive.int shouldBe 5
        page["limit"]!!.jsonPrimitive.int shouldBe 2
        page["offset"]!!.jsonPrimitive.int shouldBe 2

        run("user", "list", "--status", "suspended", "--json").json["data"]!!.jsonObject["total"]!!.jsonPrimitive.int shouldBe 1
        run("user", "list", "--query", "会員5", "--json").json["data"]!!.jsonObject["total"]!!.jsonPrimitive.int shouldBe 1
        run("user", "list", "--limit", "501", "--json").let { it.exitCode shouldBe 5; it.errorCode() shouldBe "VALIDATION" }
        run("user", "list", "--status", "deleted", "--json").exitCode shouldBe 2
    }

    @Test
    fun `user update は片方だけ変え、何も指定しなければ VALIDATION`() {
        val user = create()

        val updated = run("user", "update", "M-000001", "--name", "山田 次郎", "--actor", "8", "--json").json["data"]!!.jsonObject
        updated["name"] shouldBe JsonPrimitive("山田 次郎")
        updated["email"] shouldBe user["email"]
        updated["updatedBy"] shouldBe actor("staff", "8")
        updated["createdBy"] shouldBe actor("staff", "7")

        run("user", "update", "M-000001", "--actor", "8", "--json").let { it.exitCode shouldBe 5; it.errorCode() shouldBe "VALIDATION" }
    }

    @Test
    fun `user suspend と activate は状態を変え、許されない遷移は CONFLICT`() {
        create()

        val suspended = run("user", "suspend", "M-000001", "--reason", "不正な注文の疑い", "--actor", "9", "--json").json["data"]!!.jsonObject
        suspended["status"] shouldBe JsonPrimitive("suspended")
        suspended["suspensionReason"] shouldBe JsonPrimitive("不正な注文の疑い")
        suspended["suspendedAt"]!!.jsonPrimitive.content shouldStartWith "20"

        run("user", "suspend", "M-000001", "--actor", "9", "--json").let { it.exitCode shouldBe 4; it.errorCode() shouldBe "CONFLICT" }

        val active = run("user", "activate", "M-000001", "--actor", "9", "--json").json["data"]!!.jsonObject
        active["status"] shouldBe JsonPrimitive("active")
        active["suspendedAt"] shouldBe JsonNull
        run("user", "activate", "M-000001", "--actor", "9", "--json").exitCode shouldBe 4
    }

    @Test
    fun `user authenticate は成功で会員を返し、失敗は理由を区別せず AUTH_FAILED`() {
        create()

        val ok = run("user", "authenticate", "--email", "taro@example.com", "--password-stdin", "--json", stdin = "correct horse\n")
        ok.exitCode shouldBe 0
        ok.json["data"]!!.jsonObject.keys shouldBe setOf("authenticated", "user")
        ok.json["data"]!!.jsonObject["authenticated"] shouldBe JsonPrimitive(true)
        ok.json["data"]!!.jsonObject["user"]!!.jsonObject["displayNumber"] shouldBe JsonPrimitive("M-000001")

        val wrong = run("user", "authenticate", "--email", "taro@example.com", "--password-stdin", "--json", stdin = "wrong password\n")
        val nobody = run("user", "authenticate", "--email", "nobody@example.com", "--password-stdin", "--json", stdin = "correct horse\n")
        run("user", "suspend", "M-000001", "--actor", "1").exitCode shouldBe 0
        val suspended = run("user", "authenticate", "--email", "taro@example.com", "--password-stdin", "--json", stdin = "correct horse\n")

        listOf(wrong, nobody, suspended).forEach {
            it.exitCode shouldBe 6
            it.errorCode() shouldBe "AUTH_FAILED"
            it.json["error"] shouldBe wrong.json["error"]
        }
    }

    @Test
    fun `人向けの表示は一覧を 1 人 1 行で出し、時刻を JST で出す`() {
        create(email = "a@example.com", name = "有村")
        create(email = "b@example.com", name = "坂本")

        val list = run("user", "list")
        list.exitCode shouldBe 0
        val lines = list.out.trim().lines()
        lines[0] shouldContain "M-000001"
        lines[0] shouldContain "有効"
        lines[0] shouldContain "a@example.com"
        lines[0] shouldContain "有村"
        lines[1] shouldContain "M-000002"
        lines.last() shouldContain "全 2 件"

        val one = run("user", "get", "M-000001")
        one.out shouldContain "JST"
        one.out shouldContain "管理者 7"

        val failed = run("user", "get", "M-999999")
        failed.exitCode shouldBe 3
        failed.out shouldBe ""
        failed.err shouldContain "会員が見つかりません"
    }

    @Test
    fun `停止の理由が無ければ null`() {
        create()

        run("user", "suspend", "M-000001", "--actor", "1", "--json").json["data"]!!.jsonObject["suspensionReason"] shouldBe JsonNull
        run("user", "get", "M-000001", "--json").json["data"]!!.jsonObject["suspensionReason"] shouldBe JsonNull
    }
}
