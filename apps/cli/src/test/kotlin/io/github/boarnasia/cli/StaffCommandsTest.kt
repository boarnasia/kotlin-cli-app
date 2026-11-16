package io.github.boarnasia.cli

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class StaffCommandsTest {

    @TempDir
    lateinit var dir: Path

    private lateinit var env: Map<String, String>

    private val allPermissions = listOf(
        "member:read", "member:write", "order:read", "order:cancel-approve", "item:write", "notice:write", "staff:admin",
    )

    @BeforeEach
    fun setUp() {
        env = mapOf("CORE_DB_PATH" to dir.resolve("core.db").toString(), "CORE_APP_ID" to "3")
        cli("db", "migrate", env = env).exitCode shouldBe 0
    }

    private fun run(vararg args: String, stdin: String = "", env: Map<String, String> = this.env) = cli(*args, env = env, stdin = stdin)

    private fun bootstrap(email: String = "admin@example.com") =
        run("staff", "bootstrap", "--email", email, "--name", "最初の管理者", "--password-stdin", "--json", stdin = "correct horse\n")

    private fun create(email: String, vararg permissions: String, actor: String = "S-000001", password: String = "correct horse"): CliResult {
        val args = permissions.flatMap { listOf("--permission", it) }.toTypedArray()
        return run("staff", "create", "--email", email, "--name", "管理者", *args, "--password-stdin", "--actor", actor, "--json", stdin = "$password\n")
    }

    private val CliResult.data: JsonObject get() = json["data"]!!.jsonObject

    private fun CliResult.errorCode() = json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    private fun JsonObject.permissions() = this["permissions"]!!.jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `staff bootstrap は空の DB で 7 種類の権限を持つ S-000001 を作り、2 回目は CONFLICT（4）`() {
        val first = bootstrap()

        first.exitCode shouldBe 0
        val staff = first.data
        staff.keys shouldBe setOf(
            "id", "displayNumber", "email", "name", "status", "permissions", "suspendedAt", "suspensionReason",
            "createdAt", "updatedAt", "createdBy", "updatedBy",
        )
        staff["displayNumber"] shouldBe JsonPrimitive("S-000001")
        staff["status"] shouldBe JsonPrimitive("active")
        staff.permissions() shouldBe allPermissions
        staff["suspendedAt"] shouldBe JsonNull
        staff["createdBy"]!!.jsonObject["id"] shouldBe staff["id"]
        staff["createdBy"]!!.jsonObject["type"] shouldBe JsonPrimitive("staff")

        bootstrap("other@example.com").let { it.exitCode shouldBe 4; it.errorCode() shouldBe "CONFLICT" }
    }

    @Test
    fun `staff bootstrap は --actor を取らず、CORE_APP_ID が要る`() {
        run("staff", "bootstrap", "--email", "a@example.com", "--name", "A", "--password-stdin", "--actor", "1", "--json", stdin = "correct horse\n")
            .exitCode shouldBe 2
        run("staff", "bootstrap", "--email", "a@example.com", "--name", "A", "--password-stdin", "--json", stdin = "correct horse\n", env = env - "CORE_APP_ID")
            .exitCode shouldBe 2
    }

    @Test
    fun `出力に公開 ID・パスワード・ハッシュを含めない`() {
        val outputs = listOf(
            bootstrap(),
            run("staff", "set-password", "S-000001", "--password-stdin", "--actor", "S-000001", "--json", stdin = "battery staple\n"),
            run("staff", "authenticate", "--email", "admin@example.com", "--password-stdin", "--json", stdin = "battery staple\n"),
        )

        outputs.forEach {
            it.exitCode shouldBe 0
            it.out shouldNotContain "argon2"
            it.out shouldNotContain "battery staple"
            it.out shouldNotContain "publicId"
            it.data.toString().lowercase() shouldNotContain "password"
        }
    }

    @Test
    fun `staff create は指定した権限を決めた順に並べ、知らない権限は USAGE（2）`() {
        bootstrap()

        val staff = create("s@example.com", "order:read", "member:read", "order:read").data
        staff["displayNumber"] shouldBe JsonPrimitive("S-000002")
        staff.permissions() shouldBe listOf("member:read", "order:read")
        create("none@example.com").data.permissions() shouldBe emptyList()

        create("x@example.com", "member:delete").let { it.exitCode shouldBe 2; it.errorCode() shouldBe "USAGE" }
        create("ADMIN@example.com").let { it.exitCode shouldBe 4; it.errorCode() shouldBe "CONFLICT" }
    }

    @Test
    fun `staff list と get は番号で引け、一覧は user と同じ形`() {
        bootstrap()
        create("s@example.com", "member:read")

        val page = run("staff", "list", "--limit", "1", "--offset", "1", "--actor", "S-000001", "--json").data
        page.keys shouldBe setOf("items", "total", "limit", "offset")
        page["items"]!!.jsonArray.single().jsonObject["displayNumber"] shouldBe JsonPrimitive("S-000002")
        page["total"]!!.jsonPrimitive.int shouldBe 2

        val one = run("staff", "get", "S-000002", "--actor", "S-000001", "--json").data
        run("staff", "get", one["id"]!!.jsonPrimitive.content, "--actor", "S-000001", "--json").data shouldBe one
        run("staff", "get", "S-999999", "--actor", "S-000001", "--json").let { it.exitCode shouldBe 3; it.errorCode() shouldBe "NOT_FOUND" }
        run("staff", "get", "M-000001", "--actor", "S-000001", "--json").let { it.exitCode shouldBe 5; it.errorCode() shouldBe "VALIDATION" }
    }

    @Test
    fun `staff_admin の無い管理者は一覧と他人の取得が FORBIDDEN（7）で、自分は取り出せる`() {
        bootstrap()
        create("s@example.com", "member:read")

        run("staff", "list", "--actor", "S-000002", "--json").let { it.exitCode shouldBe 7; it.errorCode() shouldBe "FORBIDDEN" }
        run("staff", "get", "S-000001", "--actor", "S-000002", "--json").exitCode shouldBe 7
        run("staff", "get", "S-000002", "--actor", "S-000002", "--json").exitCode shouldBe 0
    }

    @Test
    fun `staff update は --grant と --revoke で権限を付け替え、両方に同じ権限は VALIDATION（5）`() {
        bootstrap()
        create("s@example.com", "member:read")

        val updated = run("staff", "update", "S-000002", "--grant", "order:read", "--grant", "item:write", "--revoke", "member:read", "--actor", "S-000001", "--json").data
        updated.permissions() shouldBe listOf("order:read", "item:write")

        run("staff", "update", "S-000002", "--grant", "order:read", "--revoke", "order:read", "--actor", "S-000001", "--json")
            .let { it.exitCode shouldBe 5; it.errorCode() shouldBe "VALIDATION" }
        run("staff", "update", "S-000002", "--actor", "S-000001", "--json").exitCode shouldBe 5
        run("staff", "update", "S-000002", "--grant", "nope", "--actor", "S-000001", "--json").exitCode shouldBe 2
    }

    @Test
    fun `自分から staff_admin を外すのは CONFLICT（4）で、ほかの権限は外せる`() {
        bootstrap()

        run("staff", "update", "S-000001", "--revoke", "staff:admin", "--actor", "S-000001", "--json")
            .let { it.exitCode shouldBe 4; it.errorCode() shouldBe "CONFLICT" }
        run("staff", "update", "S-000001", "--revoke", "order:read", "--actor", "S-000001", "--json").data.permissions() shouldBe
            allPermissions - "order:read"
    }

    @Test
    fun `権限の無い管理者も自分の名前は変えられ、自分に権限は付けられない`() {
        bootstrap()
        create("s@example.com")

        run("staff", "update", "S-000002", "--name", "新しい名前", "--actor", "S-000002", "--json").data["name"] shouldBe JsonPrimitive("新しい名前")
        run("staff", "update", "S-000002", "--grant", "member:read", "--actor", "S-000002", "--json").exitCode shouldBe 7
    }

    @Test
    fun `staff suspend と activate は状態を変え、自分と最後の staff_admin の管理者の停止は CONFLICT（4）`() {
        bootstrap()
        create("s@example.com", "member:read")

        val suspended = run("staff", "suspend", "S-000002", "--reason", "退職", "--actor", "S-000001", "--json").data
        suspended["status"] shouldBe JsonPrimitive("suspended")
        suspended["suspensionReason"] shouldBe JsonPrimitive("退職")
        run("staff", "suspend", "S-000002", "--actor", "S-000001", "--json").exitCode shouldBe 4

        // S-000001 は staff:admin を持つ最後の有効な管理者
        run("staff", "suspend", "S-000001", "--actor", "S-000001", "--json").let { it.exitCode shouldBe 4; it.errorCode() shouldBe "CONFLICT" }

        run("staff", "activate", "S-000002", "--actor", "S-000001", "--json").data["suspendedAt"] shouldBe JsonNull
        run("staff", "activate", "S-000002", "--actor", "S-000001", "--json").exitCode shouldBe 4
    }

    @Test
    fun `停止中の管理者を --actor に渡すと FORBIDDEN（7）`() {
        bootstrap()
        create("s@example.com", "staff:admin")
        run("staff", "suspend", "S-000002", "--actor", "S-000001").exitCode shouldBe 0

        run("staff", "list", "--actor", "S-000002", "--json").let { it.exitCode shouldBe 7; it.errorCode() shouldBe "FORBIDDEN" }
    }

    @Test
    fun `staff authenticate は成功で権限を含む管理者を返し、失敗は理由を区別せず AUTH_FAILED（6）`() {
        bootstrap()
        create("s@example.com", "order:read")

        val ok = run("staff", "authenticate", "--email", "s@example.com", "--password-stdin", "--json", stdin = "correct horse\n")
        ok.exitCode shouldBe 0
        ok.data.keys shouldBe setOf("authenticated", "staff")
        ok.data["authenticated"] shouldBe JsonPrimitive(true)
        ok.data["staff"]!!.jsonObject.permissions() shouldBe listOf("order:read")

        val wrong = run("staff", "authenticate", "--email", "s@example.com", "--password-stdin", "--json", stdin = "wrong password\n")
        val nobody = run("staff", "authenticate", "--email", "nobody@example.com", "--password-stdin", "--json", stdin = "correct horse\n")
        run("staff", "suspend", "S-000002", "--actor", "S-000001").exitCode shouldBe 0
        val suspended = run("staff", "authenticate", "--email", "s@example.com", "--password-stdin", "--json", stdin = "correct horse\n")

        listOf(wrong, nobody, suspended).forEach {
            it.exitCode shouldBe 6
            it.errorCode() shouldBe "AUTH_FAILED"
            it.json["error"] shouldBe wrong.json["error"]
        }
    }

    @Test
    fun `人向けの表示は一覧を 1 人 1 行で出し、権限も出す`() {
        bootstrap()
        create("s@example.com", "member:read", "order:read")
        create("none@example.com")

        val lines = run("staff", "list", "--actor", "S-000001").out.trim().lines()
        lines[0] shouldContain "S-000001"
        lines[0] shouldContain "staff:admin"
        lines[1] shouldContain "S-000002"
        lines[1] shouldContain "有効"
        lines[1] shouldContain "member:read,order:read"
        lines[2] shouldContain "（なし）"
        lines.last() shouldContain "全 3 件"

        val one = run("staff", "get", "S-000002", "--actor", "S-000001").out
        one shouldContain "権限:      member:read,order:read"
        one shouldContain "JST"
    }

    @Test
    fun `人向けの表示では登録と最終変更をした管理者を S- の番号で出す`() {
        val adminId = bootstrap().data["id"]!!.jsonPrimitive.content
        create("editor@example.com", "staff:admin")
        run("staff", "update", "S-000001", "--name", "改名", "--actor", "S-000002").exitCode shouldBe 0

        val one = run("staff", "get", "S-000001", "--actor", "S-000001").out

        one shouldContain "登録:      "
        one shouldContain "（管理者 S-000001）"
        one shouldContain "（管理者 S-000002）"
        // 内部 ID は「内部 ID:」の行にだけ出る
        one.lines().filter { "登録:" in it || "最終変更:" in it }.none { adminId in it } shouldBe true
    }
}
