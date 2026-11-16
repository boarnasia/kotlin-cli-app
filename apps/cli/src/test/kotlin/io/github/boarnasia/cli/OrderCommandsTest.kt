package io.github.boarnasia.cli

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
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

class OrderCommandsTest {

    @TempDir
    lateinit var dir: Path

    private lateinit var env: Map<String, String>

    @BeforeEach
    fun setUp() {
        env = mapOf("CORE_DB_PATH" to dir.resolve("core.db").toString(), "CORE_APP_ID" to "3")
        cli("db", "migrate", env = env).exitCode shouldBe 0
        cli("staff", "bootstrap", "--email", "admin@example.com", "--name", "管理者", "--password-stdin", env = env, stdin = "correct horse\n").exitCode shouldBe 0
        member("taro@example.com")
        item("ノート", "300", onHand = "10")
        item("ペン", "1200", onHand = "1")
    }

    private fun run(vararg args: String, env: Map<String, String> = this.env) = cli(*args, env = env)

    private fun member(email: String) =
        cli("user", "create", "--email", email, "--name", "会員", "--password-stdin", "--actor", "S-000001", env = env, stdin = "correct horse\n").exitCode shouldBe 0

    private fun item(name: String, price: String, onHand: String, publish: Boolean = true) {
        val number = run("item", "create", "--name", name, "--price", price, "--actor", "S-000001", "--json").data["displayNumber"]!!.jsonPrimitive.content
        run("stock", "adjust", number, "--delta", onHand, "--reason", "入荷", "--actor", "S-000001").exitCode shouldBe 0
        if (publish) run("item", "publish", number, "--actor", "S-000001").exitCode shouldBe 0
    }

    private fun staff(email: String, vararg permissions: String): String {
        val args = permissions.flatMap { listOf("--permission", it) }.toTypedArray()
        val result = cli("staff", "create", "--email", email, "--name", "管理者", *args, "--password-stdin", "--actor", "S-000001", "--json", env = env, stdin = "correct horse\n")
        result.exitCode shouldBe 0
        return result.data["displayNumber"]!!.jsonPrimitive.content
    }

    private fun place(vararg items: String, user: String = "M-000001", actor: String = "S-000001") =
        run("order", "place", "--user", user, *items.flatMap { listOf("--item", it) }.toTypedArray(), "--actor", actor, "--json")

    private val CliResult.data: JsonObject get() = json["data"]!!.jsonObject

    private fun CliResult.errorCode() = json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content

    private fun stock(item: String) = run("stock", "show", item, "--actor", "S-000001", "--json").data.let {
        listOf(it["onHand"], it["reserved"]).map { v -> v!!.jsonPrimitive.content.toLong() }
    }

    @Test
    fun `order place は明細と合計を持つ注文を 15 項目で出し、明細は 7 項目、履歴は 6 項目`() {
        val result = place("I-000001:2", "I-000002:1")

        result.exitCode shouldBe 0
        val order = result.data
        order.keys shouldBe setOf(
            "id", "displayNumber", "publicId", "member", "status", "total", "lines", "placedAt", "completedAt", "cancelledAt",
            "events", "createdAt", "updatedAt", "createdBy", "updatedBy",
        )
        order["displayNumber"] shouldBe JsonPrimitive("O-000001")
        order.str("publicId") shouldMatch Regex("""[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}""")
        order["member"]!!.jsonObject.keys shouldBe setOf("id", "displayNumber")
        order["member"]!!.jsonObject["displayNumber"] shouldBe JsonPrimitive("M-000001")
        order["status"] shouldBe JsonPrimitive("accepted")
        order["total"] shouldBe JsonPrimitive(1800)
        order["completedAt"] shouldBe JsonNull
        order["cancelledAt"] shouldBe JsonNull
        val lines = order["lines"]!!.jsonArray.map { it.jsonObject }
        lines[0].keys shouldBe setOf("lineNo", "itemId", "itemDisplayNumber", "name", "unitPrice", "quantity", "subtotal")
        listOf(lines[0]["lineNo"], lines[0]["itemDisplayNumber"], lines[0]["name"], lines[0]["unitPrice"], lines[0]["quantity"], lines[0]["subtotal"]) shouldBe
            listOf(JsonPrimitive(1), JsonPrimitive("I-000001"), JsonPrimitive("ノート"), JsonPrimitive(300), JsonPrimitive(2), JsonPrimitive(600))
        lines[1]["subtotal"] shouldBe JsonPrimitive(1200)
        val event = order["events"]!!.jsonArray.single().jsonObject
        event.keys shouldBe setOf("kind", "fromStatus", "toStatus", "reason", "createdAt", "createdBy")
        listOf(event["kind"], event["fromStatus"], event["toStatus"], event["reason"]) shouldBe
            listOf(JsonPrimitive("placed"), JsonNull, JsonPrimitive("accepted"), JsonNull)
        stock("I-000001") shouldBe listOf(10L, 2L)
    }

    @Test
    fun `order place の誤りは、形が 2・数と重複が 5・在庫不足と注文できない商品と停止中の会員が 4・いない会員が 3・権限が無いと 7`() {
        item("非公開", "100", onHand = "5", publish = false)

        place("I-000001").let { it.exitCode shouldBe 2; it.errorCode() shouldBe "USAGE" }
        place("I-000001:x").exitCode shouldBe 2
        place("abc:1").exitCode shouldBe 2
        run("order", "place", "--user", "M-000001", "--actor", "S-000001", "--json").exitCode shouldBe 2
        place("I-000001:1", env = env).exitCode shouldBe 0
        cli("order", "place", "--user", "M-000001", "--item", "I-000001:1", "--actor", "S-000001", "--json", env = env - "CORE_APP_ID").exitCode shouldBe 2
        place("I-000001:0").let { it.exitCode shouldBe 5; it.errorCode() shouldBe "VALIDATION" }
        place("I-000001:100").exitCode shouldBe 5
        place("I-000001:1", "I-000001:2").exitCode shouldBe 5
        place("I-000001:1", "I-000002:2").let { it.exitCode shouldBe 4; it.errorCode() shouldBe "CONFLICT" }
        stock("I-000001") shouldBe listOf(10L, 1L)
        place("I-000003:1").exitCode shouldBe 4
        place("I-000001:1", user = "M-000009").let { it.exitCode shouldBe 3; it.errorCode() shouldBe "NOT_FOUND" }
        run("user", "suspend", "M-000001", "--actor", "S-000001").exitCode shouldBe 0
        place("I-000001:1").exitCode shouldBe 4
        val reader = staff("reader@example.com", "order:read")
        place("I-000001:1", user = "M-000001", actor = reader).let { it.exitCode shouldBe 7; it.errorCode() shouldBe "FORBIDDEN" }
        run("order", "list", "--actor", "S-000001", "--json").data["total"] shouldBe JsonPrimitive(1)
    }

    private fun place(vararg items: String, env: Map<String, String>) =
        cli("order", "place", "--user", "M-000001", *items.flatMap { listOf("--item", it) }.toTypedArray(), "--actor", "S-000001", "--json", env = env)

    @Test
    fun `order get は 3 種類の ID で取り出し、他の種類の番号は 5、いなければ 3`() {
        val order = place("I-000001:1").data

        run("order", "get", order.str("id"), "--actor", "S-000001", "--json").data shouldBe order
        run("order", "get", "O-000001", "--actor", "S-000001", "--json").data shouldBe order
        run("order", "get", order.str("publicId"), "--actor", "S-000001", "--json").data shouldBe order
        run("order", "get", "I-000001", "--actor", "S-000001", "--json").exitCode shouldBe 5
        run("order", "get", "O-000009", "--actor", "S-000001", "--json").exitCode shouldBe 3
        run("order", "get", "abc", "--actor", "S-000001", "--json").exitCode shouldBe 2
    }

    @Test
    fun `order list は新しい順で、--status と --user で絞り込み、order_read が無いと 7`() {
        member("hanako@example.com")
        place("I-000001:1")
        place("I-000001:1", user = "M-000002")
        place("I-000001:1")
        run("order", "complete", "O-000003", "--actor", "S-000001").exitCode shouldBe 0

        fun numbers(vararg args: String) = run("order", "list", *args, "--actor", "S-000001", "--json").data["items"]!!.jsonArray
            .map { it.jsonObject.str("displayNumber") }
        numbers() shouldContainExactly listOf("O-000003", "O-000002", "O-000001")
        numbers("--user", "M-000001") shouldContainExactly listOf("O-000003", "O-000001")
        numbers("--status", "accepted") shouldContainExactly listOf("O-000002", "O-000001")
        numbers("--limit", "1", "--offset", "1") shouldContainExactly listOf("O-000002")
        run("order", "list", "--status", "shipped", "--actor", "S-000001", "--json").exitCode shouldBe 2
        val nobody = staff("nobody@example.com", "item:write")
        run("order", "list", "--actor", nobody, "--json").exitCode shouldBe 7
    }

    @Test
    fun `申請・却下・申請・承認で引当が戻り、履歴が 5 件になる。却下の --reason が無いと 2`() {
        place("I-000001:2")

        run("order", "request-cancel", "O-000001", "--reason", "間違えた", "--actor", "S-000001", "--json").data["status"] shouldBe JsonPrimitive("cancel_requested")
        run("order", "reject-cancel", "O-000001", "--actor", "S-000001", "--json").exitCode shouldBe 2
        run("order", "reject-cancel", "O-000001", "--reason", "", "--actor", "S-000001", "--json").exitCode shouldBe 5
        run("order", "reject-cancel", "O-000001", "--reason", "発送の準備ができている", "--actor", "S-000001", "--json").data["status"] shouldBe JsonPrimitive("accepted")
        stock("I-000001") shouldBe listOf(10L, 2L)
        run("order", "request-cancel", "O-000001", "--actor", "S-000001").exitCode shouldBe 0
        val cancelled = run("order", "approve-cancel", "O-000001", "--actor", "S-000001", "--json").data

        cancelled["status"] shouldBe JsonPrimitive("cancelled")
        cancelled["cancelledAt"] shouldNotBe JsonNull
        cancelled["events"]!!.jsonArray.map { it.jsonObject.str("kind") } shouldContainExactly
            listOf("placed", "cancel_requested", "cancel_rejected", "cancel_requested", "cancel_approved")
        cancelled["events"]!!.jsonArray[2].jsonObject["reason"] shouldBe JsonPrimitive("発送の準備ができている")
        stock("I-000001") shouldBe listOf(10L, 0L)
    }

    @Test
    fun `完了すると在庫数と引当が減り、許されない遷移は 4`() {
        place("I-000001:2")
        place("I-000001:1")
        run("order", "request-cancel", "O-000002", "--actor", "S-000001").exitCode shouldBe 0

        run("order", "complete", "O-000002", "--actor", "S-000001", "--json").let { it.exitCode shouldBe 4; it.errorCode() shouldBe "CONFLICT" }
        run("order", "approve-cancel", "O-000001", "--actor", "S-000001", "--json").exitCode shouldBe 4
        val completed = run("order", "complete", "O-000001", "--actor", "S-000001", "--json").data
        completed["status"] shouldBe JsonPrimitive("completed")
        completed["completedAt"] shouldNotBe JsonNull
        stock("I-000001") shouldBe listOf(8L, 1L)
        run("order", "complete", "O-000001", "--actor", "S-000001", "--json").exitCode shouldBe 4
    }

    @Test
    fun `権限の割り当ては、order_read は見るだけ、order_write は作成・申請・完了、order_cancel-approve は承認と却下`() {
        val reader = staff("reader@example.com", "order:read")
        val writer = staff("writer@example.com", "order:write")
        val approver = staff("approver@example.com", "order:cancel-approve")
        place("I-000001:1", actor = writer).exitCode shouldBe 0

        run("order", "get", "O-000001", "--actor", reader, "--json").exitCode shouldBe 0
        run("order", "complete", "O-000001", "--actor", reader, "--json").exitCode shouldBe 7
        run("order", "request-cancel", "O-000001", "--actor", reader, "--json").exitCode shouldBe 7
        run("order", "request-cancel", "O-000001", "--actor", writer, "--json").exitCode shouldBe 0
        run("order", "approve-cancel", "O-000001", "--actor", writer, "--json").exitCode shouldBe 7
        run("order", "reject-cancel", "O-000001", "--reason", "理由", "--actor", approver, "--json").exitCode shouldBe 0
        place("I-000001:1", actor = approver).exitCode shouldBe 7
        run("order", "complete", "O-000001", "--actor", approver, "--json").exitCode shouldBe 7
        run("order", "complete", "O-000001", "--actor", writer, "--json").exitCode shouldBe 0
    }

    @Test
    fun `人向けの表示は一覧を 1 注文 1 行で、get は明細と履歴と操作した人の番号を出し、在庫の履歴は注文を番号で出す`() {
        place("I-000001:2", "I-000002:1")
        val approver = staff("approver@example.com", "order:cancel-approve")
        run("order", "request-cancel", "O-000001", "--reason", "間違えた", "--actor", "S-000001").exitCode shouldBe 0
        run("order", "reject-cancel", "O-000001", "--reason", "発送済み", "--actor", approver).exitCode shouldBe 0

        val lines = run("order", "list", "--actor", "S-000001").out.trim().lines()
        lines[0] shouldMatch Regex("""O-000001 {2}受付済み {2}1,800 円 {2}M-000001 {2}\d{4}-\d\d-\d\d \d\d:\d\d:\d\d JST {2}ノート×2 ほか 1 件""")
        lines.last() shouldBe "全 1 件（1〜1 件目）"

        val one = run("order", "get", "O-000001", "--actor", "S-000001").out
        one shouldContain "合計:      1,800 円"
        one shouldContain "    1. I-000001  ノート  300 円 × 2 = 600 円"
        one shouldContain "    2. I-000002  ペン  1,200 円 × 1 = 1,200 円"
        one shouldContain "作成  受付済み  管理者 S-000001"
        one shouldContain "キャンセルの申請  受付済み→キャンセル申請中  間違えた  管理者 S-000001"
        one shouldContain "キャンセルの却下  キャンセル申請中→受付済み  発送済み  管理者 $approver"
        one shouldContain "最終変更:  "
        one shouldContain "（管理者 $approver）"

        run("stock", "history", "I-000001", "--actor", "S-000001").out.trim().lines()[0] shouldContain "引当 +2→2  注文 O-000001  管理者 S-000001"
    }
}
