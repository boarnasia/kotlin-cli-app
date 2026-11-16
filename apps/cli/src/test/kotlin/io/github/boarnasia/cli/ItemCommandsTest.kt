package io.github.boarnasia.cli

import io.github.boarnasia.core.db.Actor
import io.github.boarnasia.core.db.CoreDb
import io.github.boarnasia.core.db.id.SnowflakeIdGenerator
import io.github.boarnasia.core.item.StockLedger
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import java.nio.file.Path
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ItemCommandsTest {

    @TempDir
    lateinit var dir: Path

    private lateinit var env: Map<String, String>

    @BeforeEach
    fun setUp() {
        env = mapOf("CORE_DB_PATH" to dir.resolve("core.db").toString(), "CORE_APP_ID" to "3")
        cli("db", "migrate", env = env).exitCode shouldBe 0
        cli("staff", "bootstrap", "--email", "admin@example.com", "--name", "管理者", "--password-stdin", env = env, stdin = "correct horse\n").exitCode shouldBe 0
    }

    private fun run(vararg args: String, env: Map<String, String> = this.env) = cli(*args, env = env)

    /** 管理者を作り、その S- の番号を返す。 */
    private fun staff(email: String, vararg permissions: String): String {
        val args = permissions.flatMap { listOf("--permission", it) }.toTypedArray()
        val result = cli("staff", "create", "--email", email, "--name", "管理者", *args, "--password-stdin", "--actor", "S-000001", "--json", env = env, stdin = "correct horse\n")
        result.exitCode shouldBe 0
        return result.json["data"]!!.jsonObject["displayNumber"]!!.jsonPrimitive.content
    }

    private fun create(vararg extra: String, name: String = "ノート", price: String = "300"): JsonObject {
        val result = run("item", "create", "--name", name, "--price", price, *extra, "--actor", "S-000001", "--json")
        result.exitCode shouldBe 0
        return result.data
    }

    private val CliResult.data: JsonObject get() = json["data"]!!.jsonObject

    private fun CliResult.errorCode() = json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content

    /** core の StockLedger で、商品に注文の引当を作る。 */
    private fun reserve(itemId: String, quantity: Long) {
        val db = CoreDb.openFile(dir.resolve("core.db"))
        db.write { StockLedger(SnowflakeIdGenerator(1)).reserve(it, itemId.toLong(), quantity, 900, Actor.Member(500)) }
    }

    @Test
    fun `item create は非公開の商品を 13 項目で出し、在庫は 0 で、公開 ID を出さない`() {
        val item = create("--description", "A5 の方眼")

        item.keys shouldBe setOf(
            "id", "displayNumber", "name", "description", "price", "status", "publishedAt", "deletedAt", "stock",
            "createdAt", "updatedAt", "createdBy", "updatedBy",
        )
        item["displayNumber"] shouldBe JsonPrimitive("I-000001")
        item["price"] shouldBe JsonPrimitive(300)
        item["status"] shouldBe JsonPrimitive("unpublished")
        item["publishedAt"] shouldBe JsonNull
        item["deletedAt"] shouldBe JsonNull
        item["description"] shouldBe JsonPrimitive("A5 の方眼")
        item["stock"] shouldBe buildJsonObject { put("onHand", 0); put("reserved", 0); put("available", 0) }
        item["createdBy"]!!.jsonObject["type"] shouldBe JsonPrimitive("staff")
        item.str("createdAt") shouldMatch Regex("""\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3}Z""")
        SnowflakeIdGenerator.decompose(item.str("id").toLong()).appId shouldBe 3
        create(name = "ペン")["description"] shouldBe JsonNull
    }

    @Test
    fun `item create の誤りは、整数でない価格が 2・範囲の外が 5・CORE_APP_ID が無いと 2・商品の書き込みの権限が無いと 7`() {
        run("item", "create", "--name", "ノート", "--price", "300円", "--actor", "S-000001", "--json").let {
            it.exitCode shouldBe 2
            it.errorCode() shouldBe "USAGE"
        }
        run("item", "create", "--name", "ノート", "--price", "-1", "--actor", "S-000001", "--json").let {
            it.exitCode shouldBe 5
            it.errorCode() shouldBe "VALIDATION"
        }
        run("item", "create", "--name", "ノート", "--price", "1", "--actor", "S-000001", "--json", env = env - "CORE_APP_ID").exitCode shouldBe 2
        val viewer = staff("viewer@example.com", "member:write")
        run("item", "create", "--name", "ノート", "--price", "1", "--actor", viewer, "--json").let {
            it.exitCode shouldBe 7
            it.errorCode() shouldBe "FORBIDDEN"
        }
        run("item", "list", "--status", "unpublished", "--actor", "S-000001", "--json").data["total"] shouldBe JsonPrimitive(0)
    }

    @Test
    fun `item get は内部 ID と I- の番号で取り出し、ID の形でなければ 2・他の種類の番号と UUID は 5・いなければ 3`() {
        val item = create()

        run("item", "get", item.str("id"), "--actor", "S-000001", "--json").data shouldBe item
        run("item", "get", "I-000001", "--actor", "S-000001", "--json").data shouldBe item
        run("item", "get", "abc", "--actor", "S-000001", "--json").exitCode shouldBe 2
        run("item", "get", "M-000001", "--actor", "S-000001", "--json").let {
            it.exitCode shouldBe 5
            it.errorCode() shouldBe "VALIDATION"
        }
        run("item", "get", "6f1c2d3e-4a5b-4c6d-8e9f-0a1b2c3d4e5f", "--actor", "S-000001", "--json").exitCode shouldBe 5
        run("item", "get", "I-000009", "--actor", "S-000001", "--json").let {
            it.exitCode shouldBe 3
            it.errorCode() shouldBe "NOT_FOUND"
        }
    }

    @Test
    fun `item list は削除済みを除き、--status deleted で削除済みだけを出し、権限の無い管理者も見られる`() {
        create(name = "ノート")
        create(name = "ペン")
        create(name = "消しゴム")
        run("item", "delete", "I-000002", "--yes", "--actor", "S-000001").exitCode shouldBe 0
        val viewer = staff("viewer@example.com")

        val list = run("item", "list", "--actor", viewer, "--json")
        list.exitCode shouldBe 0
        list.data.keys shouldBe setOf("items", "total", "limit", "offset")
        list.data["items"]!!.jsonArray.map { it.jsonObject.str("displayNumber") } shouldContainExactly listOf("I-000001", "I-000003")
        list.data["total"] shouldBe JsonPrimitive(2)
        run("item", "list", "--status", "deleted", "--actor", "S-000001", "--json").data["items"]!!.jsonArray
            .map { it.jsonObject.str("displayNumber") } shouldContainExactly listOf("I-000002")
        run("item", "list", "--query", "ペン", "--actor", "S-000001", "--json").data["total"] shouldBe JsonPrimitive(0)
        run("item", "list", "--limit", "1", "--offset", "1", "--actor", "S-000001", "--json").data["items"]!!.jsonArray
            .map { it.jsonObject.str("displayNumber") } shouldContainExactly listOf("I-000003")
    }

    @Test
    fun `item update は属性を変え、--description "" で説明を消し、何も指定しなければ 5`() {
        create("--description", "A5 の方眼")

        val updated = run("item", "update", "I-000001", "--price", "1200", "--name", "ノート 2", "--actor", "S-000001", "--json")
        updated.exitCode shouldBe 0
        updated.data["price"] shouldBe JsonPrimitive(1200)
        updated.data["name"] shouldBe JsonPrimitive("ノート 2")
        updated.data["description"] shouldBe JsonPrimitive("A5 の方眼")
        run("item", "update", "I-000001", "--description", "", "--actor", "S-000001", "--json").data["description"] shouldBe JsonNull
        run("item", "update", "I-000001", "--actor", "S-000001", "--json").let {
            it.exitCode shouldBe 5
            it.errorCode() shouldBe "VALIDATION"
        }
    }

    @Test
    fun `item publish と unpublish は状態と公開の時刻を変え、許されない遷移は 4`() {
        create()

        val published = run("item", "publish", "I-000001", "--actor", "S-000001", "--json")
        published.data["status"] shouldBe JsonPrimitive("published")
        published.data["publishedAt"] shouldNotBe JsonNull
        run("item", "publish", "I-000001", "--actor", "S-000001", "--json").let {
            it.exitCode shouldBe 4
            it.errorCode() shouldBe "CONFLICT"
        }
        val unpublished = run("item", "unpublish", "I-000001", "--actor", "S-000001", "--json")
        unpublished.data["status"] shouldBe JsonPrimitive("unpublished")
        unpublished.data["publishedAt"] shouldBe JsonNull
        run("item", "unpublish", "I-000001", "--actor", "S-000001", "--json").exitCode shouldBe 4
    }

    @Test
    fun `item delete は --yes が無ければ 2 で何もせず、引当があれば 4、削除済みへの操作も 4`() {
        val item = create()
        run("item", "publish", "I-000001", "--actor", "S-000001").exitCode shouldBe 0
        run("stock", "adjust", "I-000001", "--delta", "5", "--reason", "入荷", "--actor", "S-000001").exitCode shouldBe 0

        run("item", "delete", "I-000001", "--actor", "S-000001", "--json").let {
            it.exitCode shouldBe 2
            it.errorCode() shouldBe "USAGE"
        }
        run("item", "get", "I-000001", "--actor", "S-000001", "--json").data["status"] shouldBe JsonPrimitive("published")

        reserve(item.str("id"), 2)
        run("item", "delete", "I-000001", "--yes", "--actor", "S-000001", "--json").let {
            it.exitCode shouldBe 4
            it.errorCode() shouldBe "CONFLICT"
        }

        val other = create(name = "ペン")
        val deleted = run("item", "delete", "I-000002", "--yes", "--actor", "S-000001", "--json")
        deleted.exitCode shouldBe 0
        deleted.data["status"] shouldBe JsonPrimitive("deleted")
        deleted.data["deletedAt"] shouldNotBe JsonNull
        deleted.data["id"] shouldBe other["id"]
        run("item", "update", "I-000002", "--name", "x", "--actor", "S-000001", "--json").exitCode shouldBe 4
        run("item", "publish", "I-000002", "--actor", "S-000001", "--json").exitCode shouldBe 4
        run("item", "delete", "I-000002", "--yes", "--actor", "S-000001", "--json").exitCode shouldBe 4
    }

    @Test
    fun `人向けの表示は一覧を 1 商品 1 行で出し、get は JST の時刻と操作した人の番号を出す`() {
        create(name = "ノート", price = "1200")
        create(name = "ペン")
        run("stock", "adjust", "I-000001", "--delta", "10", "--reason", "入荷", "--actor", "S-000001").exitCode shouldBe 0
        val editor = staff("editor@example.com", "item:write")
        run("item", "publish", "I-000001", "--actor", editor).exitCode shouldBe 0

        val lines = run("item", "list", "--actor", "S-000001").out.trim().lines()
        lines[0] shouldBe "I-000001  公開  1,200 円  在庫 10（注文可能 10）  ノート"
        lines[1] shouldBe "I-000002  非公開  300 円  在庫 0（注文可能 0）  ペン"
        lines.last() shouldBe "全 2 件（1〜2 件目）"

        val one = run("item", "get", "I-000001", "--actor", "S-000001").out
        one shouldContain "在庫:      在庫数 10・引当 0・注文可能数 10"
        one shouldContain "公開:      "
        one shouldContain "JST"
        one shouldContain "（管理者 S-000001）"
        one shouldContain "（管理者 $editor）"
    }
}
