package io.github.boarnasia.cli

import io.github.boarnasia.core.db.Actor
import io.github.boarnasia.core.db.CoreDb
import io.github.boarnasia.core.db.id.SnowflakeIdGenerator
import io.github.boarnasia.core.item.StockLedger
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
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

class StockCommandsTest {

    @TempDir
    lateinit var dir: Path

    private lateinit var env: Map<String, String>
    private lateinit var itemId: String

    @BeforeEach
    fun setUp() {
        env = mapOf("CORE_DB_PATH" to dir.resolve("core.db").toString(), "CORE_APP_ID" to "3")
        cli("db", "migrate", env = env).exitCode shouldBe 0
        cli("staff", "bootstrap", "--email", "admin@example.com", "--name", "管理者", "--password-stdin", env = env, stdin = "correct horse\n").exitCode shouldBe 0
        itemId = run("item", "create", "--name", "ノート", "--price", "300", "--actor", "S-000001", "--json").data["id"]!!.jsonPrimitive.content
    }

    private fun run(vararg args: String, env: Map<String, String> = this.env) = cli(*args, env = env)

    private fun adjust(delta: String, reason: String = "入荷", actor: String = "S-000001") =
        run("stock", "adjust", "I-000001", "--delta", delta, "--reason", reason, "--actor", actor, "--json")

    private val CliResult.data: JsonObject get() = json["data"]!!.jsonObject

    private fun CliResult.errorCode() = json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    private fun reserve(quantity: Long) {
        run("item", "publish", "I-000001", "--actor", "S-000001").exitCode shouldBe 0
        CoreDb.openFile(dir.resolve("core.db")).write {
            StockLedger(SnowflakeIdGenerator(1)).reserve(it, itemId.toLong(), quantity, 900, Actor.Member(500))
        }
    }

    @Test
    fun `stock show は在庫の 6 項目を出し、available は onHand − reserved`() {
        adjust("10").exitCode shouldBe 0
        reserve(3)

        val stock = run("stock", "show", "I-000001", "--actor", "S-000001", "--json").data

        stock.keys shouldBe setOf("itemId", "displayNumber", "onHand", "reserved", "available", "updatedAt")
        stock["itemId"] shouldBe JsonPrimitive(itemId)
        stock["displayNumber"] shouldBe JsonPrimitive("I-000001")
        listOf(stock["onHand"], stock["reserved"], stock["available"]) shouldBe listOf(JsonPrimitive(10), JsonPrimitive(3), JsonPrimitive(7))
    }

    @Test
    fun `stock adjust は {stock, movement} を出し、変動は 11 項目`() {
        val result = adjust("10")

        result.exitCode shouldBe 0
        result.data.keys shouldBe setOf("stock", "movement")
        result.data["stock"]!!.jsonObject["onHand"] shouldBe JsonPrimitive(10)
        val movement = result.data["movement"]!!.jsonObject
        movement.keys shouldBe setOf(
            "id", "itemId", "kind", "onHandDelta", "reservedDelta", "onHandAfter", "reservedAfter", "reason", "orderId", "createdAt", "createdBy",
        )
        movement["kind"] shouldBe JsonPrimitive("adjust")
        movement["onHandDelta"] shouldBe JsonPrimitive(10)
        movement["reservedDelta"] shouldBe JsonPrimitive(0)
        movement["onHandAfter"] shouldBe JsonPrimitive(10)
        movement["reason"] shouldBe JsonPrimitive("入荷")
        movement["orderId"] shouldBe JsonNull
        movement["itemId"] shouldBe JsonPrimitive(itemId)
        movement["createdBy"]!!.jsonObject["type"] shouldBe JsonPrimitive("staff")
    }

    @Test
    fun `負の --delta は --delta -3 と --delta=-3 の両方で受け付ける`() {
        adjust("10").exitCode shouldBe 0

        adjust("-3").data["stock"]!!.jsonObject["onHand"] shouldBe JsonPrimitive(7)
        run("stock", "adjust", "I-000001", "--delta=-2", "--reason", "破損", "--actor", "S-000001", "--json")
            .data["stock"]!!.jsonObject["onHand"] shouldBe JsonPrimitive(5)
    }

    @Test
    fun `stock adjust の誤りは、整数でない増減が 2・増減 0 と理由の誤りが 5・引当を下回ると 4・商品の書き込みの権限が無いと 7`() {
        adjust("1.5").let { it.exitCode shouldBe 2; it.errorCode() shouldBe "USAGE" }
        adjust("0").let { it.exitCode shouldBe 5; it.errorCode() shouldBe "VALIDATION" }
        adjust("1", reason = " ").exitCode shouldBe 5
        adjust("5").exitCode shouldBe 0
        reserve(3)
        adjust("-3").let { it.exitCode shouldBe 4; it.errorCode() shouldBe "CONFLICT" }
        val viewer = cli("staff", "create", "--email", "v@example.com", "--name", "閲覧", "--password-stdin", "--actor", "S-000001", "--json", env = env, stdin = "correct horse\n")
            .data["displayNumber"]!!.jsonPrimitive.content
        adjust("1", actor = viewer).let { it.exitCode shouldBe 7; it.errorCode() shouldBe "FORBIDDEN" }

        run("stock", "show", "I-000001", "--actor", viewer, "--json").data["onHand"] shouldBe JsonPrimitive(5)
        run("stock", "history", "I-000001", "--actor", viewer, "--json").data["total"] shouldBe JsonPrimitive(2)
    }

    @Test
    fun `stock history は新しい順で、件数と読み飛ばしを受け取り、削除済みの商品も見られる`() {
        adjust("10").exitCode shouldBe 0
        adjust("-2", reason = "破損").exitCode shouldBe 0
        run("item", "delete", "I-000001", "--yes", "--actor", "S-000001").exitCode shouldBe 0

        val history = run("stock", "history", "I-000001", "--actor", "S-000001", "--json").data
        history.keys shouldBe setOf("items", "total", "limit", "offset")
        history["items"]!!.jsonArray.map { it.jsonObject["onHandDelta"] } shouldContainExactly listOf(JsonPrimitive(-2), JsonPrimitive(10))
        history["total"] shouldBe JsonPrimitive(2)
        run("stock", "history", "I-000001", "--limit", "1", "--offset", "1", "--actor", "S-000001", "--json").data["items"]!!.jsonArray
            .map { it.jsonObject["reason"] } shouldContainExactly listOf(JsonPrimitive("入荷"))
        run("stock", "show", "I-000001", "--actor", "S-000001", "--json").data["onHand"] shouldBe JsonPrimitive(8)
    }

    @Test
    fun `人向けの表示は在庫の 3 つの数と、履歴を 1 変動 1 行で出し、操作した人を番号で出す`() {
        adjust("10").exitCode shouldBe 0
        adjust("-2", reason = "破損").exitCode shouldBe 0
        reserve(3)

        val show = run("stock", "show", "I-000001", "--actor", "S-000001").out
        show shouldContain "在庫数:      8"
        show shouldContain "引当:        3"
        show shouldContain "注文可能数:  5"

        val lines = run("stock", "history", "I-000001", "--actor", "S-000001").out.trim().lines()
        lines[0] shouldMatch Regex("""\d{4}-\d\d-\d\d \d\d:\d\d:\d\d JST {2}引当 {2}在庫数 0→8 {2}引当 \+3→3 {2}注文 900 {2}会員本人 500""")
        lines[1] shouldMatch Regex(""".* JST {2}調整 {2}在庫数 -2→8 {2}引当 0→0 {2}破損 {2}管理者 S-000001""")
        lines[2] shouldContain "調整  在庫数 +10→10  引当 0→0  入荷  管理者 S-000001"
        lines.last() shouldBe "全 3 件（1〜3 件目）"

        val adjusted = run("stock", "adjust", "I-000001", "--delta", "1", "--reason", "入荷", "--actor", "S-000001").out
        adjusted shouldContain "注文可能数:  6"
        adjusted shouldContain "変動: "
        adjusted shouldContain "管理者 S-000001"
    }
}
