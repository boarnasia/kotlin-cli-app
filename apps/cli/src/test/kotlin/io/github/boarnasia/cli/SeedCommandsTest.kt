package io.github.boarnasia.cli

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SeedCommandsTest {

    @TempDir
    lateinit var dir: Path

    private lateinit var env: Map<String, String>

    @BeforeEach
    fun setUp() {
        env = mapOf("CORE_DB_PATH" to dir.resolve("core.db").toString(), "CORE_APP_ID" to "3")
        cli("db", "migrate", env = env).exitCode shouldBe 0
    }

    private fun run(vararg args: String, stdin: String = "") = cli(*args, env = env, stdin = stdin)

    private val CliResult.data: JsonObject get() = json["data"]!!.jsonObject

    private fun CliResult.errorCode() = json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content

    private fun bootstrap() =
        run("staff", "bootstrap", "--email", "admin@example.com", "--name", "管理者", "--password-stdin", stdin = "correct horse\n").exitCode shouldBe 0

    /** 定義ファイルを置いたディレクトリを作る。ファイル名は staff.json などのモジュール名。 */
    private fun defs(vararg files: Pair<String, String>): String {
        val d = Files.createTempDirectory(dir, "seed")
        files.forEach { (module, text) -> Files.writeString(d.resolve("$module.json"), text) }
        return d.toString()
    }

    private fun total(vararg args: String) = run(*args, "--actor", "S-000001", "--json").data["total"]!!.jsonPrimitive.content.toInt()

    private fun items(vararg args: String) = run(*args, "--actor", "S-000001", "--json").data["items"]!!.jsonArray.map { it.jsonObject }

    private fun createdNumbers(result: CliResult, module: Int = 0) =
        result.data["modules"]!!.jsonArray[module].jsonObject["created"]!!.jsonArray.map { it.jsonObject.str("displayNumber") }

    private val oneUser = """{"users":[{"email":"a@example.com","name":"会員 A","password":"demo-pass-901"}]}"""

    private val twoItems = """{"items":[{"name":"ノート","price":300,"stock":3,"status":"published"},{"name":"ペン","price":100,"stock":1,"status":"published"}]}"""

    @Test
    fun `seed all は空の DB に同梱の定義を全て投入し、操作した人は最初の管理者になる`() {
        val result = run("seed", "all", "--json")

        result.exitCode shouldBe 0
        result.data["modules"]!!.jsonArray.map { m -> m.jsonObject.str("module") to m.jsonObject["created"]!!.jsonArray.size } shouldContainExactly
            listOf("staff" to 7, "user" to 10, "item" to 8, "order" to 12, "notice" to 6)

        val staff = items("staff", "list", "--limit", "500")
        staff.first().str("displayNumber") shouldBe "S-000001"
        staff.first()["permissions"]!!.jsonArray.size shouldBe 8
        staff.drop(1).map { it["createdBy"]!!.jsonObject.str("id") }.toSet() shouldBe setOf(staff.first().str("id"))
        val members = items("user", "list")
        members.map { it["createdBy"]!!.jsonObject.str("id") }.toSet() shouldBe setOf(staff.first().str("id"))
        members.count { it.str("status") == "suspended" } shouldBe 1
    }

    @Test
    fun `seed all の注文・商品・在庫・お知らせは定義のとおりの状態になる`() {
        run("seed", "all").exitCode shouldBe 0

        items("order", "list").map { it.str("status") }.groupingBy { it }.eachCount() shouldBe
            mapOf("accepted" to 4, "cancel_requested" to 1, "cancelled" to 2, "completed" to 5)
        items("item", "list", "--status", "deleted").map { it.str("name") } shouldContainExactly listOf("旧型の定規")
        items("notice", "list").map { it.str("status") } shouldContainExactlyInAnyOrder listOf("draft", "scheduled", "active", "active", "ended")
        items("notice", "list", "--status", "deleted").size shouldBe 1
        items("notice", "visible").map { it.str("title") } shouldContainExactlyInAnyOrder listOf("送料無料キャンペーン", "ご利用ガイドを更新しました")

        val history = items("stock", "history", "I-000001")
        history.last().str("kind") shouldBe "adjust"
        history.last().str("reason") shouldBe "初期在庫"
        // ノートは初期在庫 50、完了 3 件で 1+3+1 個を出荷し、受付済み 1 件で 2 個を引き当てている
        run("stock", "show", "I-000001", "--actor", "S-000001", "--json").data.let {
            listOf(it.str("onHand"), it.str("reserved")) shouldBe listOf("45", "2")
        }
    }

    @Test
    fun `同梱の定義で入れた管理者と会員はパスワードで認証できる`() {
        run("seed", "all").exitCode shouldBe 0

        run("staff", "authenticate", "--email", "admin@example.com", "--password-stdin", stdin = "demo-pass-001\n").exitCode shouldBe 0
        run("staff", "authenticate", "--email", "orders@example.com", "--password-stdin", stdin = "demo-pass-004\n").exitCode shouldBe 0
        run("user", "authenticate", "--email", "member01@example.com", "--password-stdin", stdin = "demo-pass-101\n").exitCode shouldBe 0
        run("user", "authenticate", "--email", "member01@example.com", "--password-stdin", stdin = "demo-pass-102\n").exitCode shouldBe 6
    }

    @Test
    fun `2 回目の seed all とデータのあるモジュールへの seed は何も書かずに 4`() {
        run("seed", "all").exitCode shouldBe 0

        val again = run("seed", "all", "--json")
        again.exitCode shouldBe 4
        again.errorCode() shouldBe "CONFLICT"
        again.json["error"]!!.jsonObject.str("message") shouldContain "db reset --yes"
        run("seed", "item", "--actor", "S-000001").exitCode shouldBe 4
        total("staff", "list") shouldBe 7
        total("item", "list") shouldBe 7
    }

    @Test
    fun `一部のモジュールにだけデータがある DB への seed all は管理者も作らずに 4`() {
        bootstrap()
        run("item", "create", "--name", "ノート", "--price", "300", "--actor", "S-000001").exitCode shouldBe 0

        run("seed", "all").exitCode shouldBe 4
        run("seed", "staff").exitCode shouldBe 4
        total("staff", "list") shouldBe 1
        total("user", "list") shouldBe 0
    }

    @Test
    fun `--dir の定義ファイルを使い、seed user は --actor の管理者として登録する`() {
        bootstrap()
        val users = """{"users":[
            {"email":"a@example.com","name":"会員 A","password":"demo-pass-901"},
            {"email":"b@example.com","name":"会員 B","password":"demo-pass-902","status":"suspended","suspendReason":"テスト"}]}"""

        val result = run("seed", "user", "--dir", defs("user" to users), "--actor", "S-000001", "--json")

        result.exitCode shouldBe 0
        createdNumbers(result) shouldContainExactly listOf("M-000001", "M-000002")
        result.data["modules"]!!.jsonArray[0].jsonObject["created"]!!.jsonArray[0].jsonObject["publicId"] shouldBe
            items("user", "list")[0]["publicId"]
        run("user", "get", "M-000002", "--actor", "S-000001", "--json").data.let {
            it.str("status") shouldBe "suspended"
            it.str("suspensionReason") shouldBe "テスト"
        }
    }

    @Test
    fun `seed item は初期在庫を調整してから公開・削除し、data は番号を並べる`() {
        bootstrap()
        val defs = """{"items":[
            {"name":"ノート","price":300,"stock":10,"status":"published"},
            {"name":"付箋","price":200},
            {"name":"定規","price":250,"stock":2,"status":"deleted"}]}"""

        val result = run("seed", "item", "--dir", defs("item" to defs), "--actor", "S-000001", "--json")

        result.exitCode shouldBe 0
        result.data["modules"]!!.jsonArray[0].jsonObject.str("module") shouldBe "item"
        createdNumbers(result) shouldContainExactly listOf("I-000001", "I-000002", "I-000003")
        result.data["modules"]!!.jsonArray[0].jsonObject["created"]!!.jsonArray[0].jsonObject["publicId"] shouldBe JsonNull
        items("item", "list").map { it.str("status") } shouldContainExactlyInAnyOrder listOf("published", "unpublished")
        items("stock", "history", "I-000002").size shouldBe 0
        items("stock", "history", "I-000003").map { it.str("reason") } shouldContainExactly listOf("初期在庫")
    }

    @Test
    fun `seed order だけを実行すると会員と商品を DB から引き、操作の並びで状態を作る`() {
        bootstrap()
        run("seed", "user", "--dir", defs("user" to oneUser), "--actor", "S-000001").exitCode shouldBe 0
        run("seed", "item", "--dir", defs("item" to twoItems), "--actor", "S-000001").exitCode shouldBe 0
        val orders = """{"orders":[
            {"member":"A@example.com","lines":[{"item":"ノート","quantity":1}],"steps":[{"op":"request-cancel","reason":"数を間違えた"},{"op":"approve-cancel"}]},
            {"member":"a@example.com","lines":[{"item":"ノート","quantity":1}],"steps":[{"op":"request-cancel"},{"op":"reject-cancel","reason":"発送済み"},{"op":"complete"}]}]}"""

        val result = run("seed", "order", "--dir", defs("order" to orders), "--actor", "S-000001")

        result.exitCode shouldBe 0
        result.out.trim() shouldBe "order: 2 件（O-000001〜O-000002）"
        run("order", "get", "O-000001", "--actor", "S-000001", "--json").data.let { o ->
            o.str("status") shouldBe "cancelled"
            o["events"]!!.jsonArray.size shouldBe 3
        }
        run("order", "get", "O-000002", "--actor", "S-000001", "--json").data.str("status") shouldBe "completed"
        run("stock", "show", "I-000001", "--actor", "S-000001", "--json").data.let {
            listOf(it.str("onHand"), it.str("reserved")) shouldBe listOf("2", "0")
        }
    }

    @Test
    fun `seed notice は相対の日時・今すぐ終了・論理削除でお知らせの状態を作る`() {
        bootstrap()
        val notices = """{"notices":[
            {"title":"下書き","body":"本文"},
            {"title":"予約","body":"本文","start":"P1D","end":"P2D"},
            {"title":"公開中","body":"本文","start":"-PT1H"},
            {"title":"終了","body":"本文","start":"-P1D","end":"P1D","endNow":true},
            {"title":"削除","body":"本文","deleted":true}]}"""

        run("seed", "notice", "--dir", defs("notice" to notices), "--actor", "S-000001").exitCode shouldBe 0

        items("notice", "list").associate { it.str("title") to it.str("status") } shouldBe
            mapOf("下書き" to "draft", "予約" to "scheduled", "公開中" to "active", "終了" to "ended")
        items("notice", "list", "--status", "deleted").map { it.str("title") } shouldContainExactly listOf("削除")
    }

    @Test
    fun `定義ファイルの形の誤りは何も書かずに 5`() {
        bootstrap()
        val cases = listOf(
            "user" to "{",
            "user" to """{"users":[{"email":"a@example.com","name":"会員","password":"demo-pass-901","colour":"red"}]}""",
            "user" to """{"users":[{"email":"a@example.com","name":"会員"}]}""",
            "user" to """{"users":[{"email":"a@example.com","name":"会員","password":"demo-pass-901","status":"paused"}]}""",
        )
        cases.forEach { (module, text) ->
            val result = run("seed", module, "--dir", defs(module to text), "--actor", "S-000001", "--json")
            result.exitCode shouldBe 5
            result.errorCode() shouldBe "VALIDATION"
            result.json["error"]!!.jsonObject.str("message") shouldContain "user.json"
        }
        run("seed", "user", "--dir", defs(), "--actor", "S-000001").exitCode shouldBe 5
        run("seed", "notice", "--dir", defs("notice" to """{"notices":[{"title":"t","body":"b","end":"3 days"}]}"""), "--actor", "S-000001").exitCode shouldBe 5
        total("user", "list") shouldBe 0
        total("notice", "list") shouldBe 0
    }

    @Test
    fun `管理者の権限の名前の誤りは管理者を作らずに 5`() {
        val staff = """{"bootstrap":{"email":"admin@example.com","name":"管理者","password":"demo-pass-001"},
            "staff":[{"email":"s@example.com","name":"担当","password":"demo-pass-002","permissions":["item:read"]}]}"""

        run("seed", "staff", "--dir", defs("staff" to staff)).exitCode shouldBe 5
        run("seed", "staff", "--dir", defs("staff" to staff.replace("item:read", "item:write"))).exitCode shouldBe 0
        total("staff", "list") shouldBe 2
    }

    @Test
    fun `注文の参照の誤りは何も書かずに 5`() {
        val dirWith = { orders: String, items: String ->
            val bundled = Path.of(javaClass.getResource("/seed/staff.json")!!.toURI()).parent
            val d = defs("order" to orders, "item" to items, "user" to oneUser)
            listOf("staff", "notice").forEach { Files.copy(bundled.resolve("$it.json"), Path.of(d).resolve("$it.json")) }
            d
        }
        val unknownMember = """{"orders":[{"member":"nobody@example.com","lines":[{"item":"ノート","quantity":1}]}]}"""
        val ambiguous = """{"orders":[{"member":"a@example.com","lines":[{"item":"ノート","quantity":1}]}]}"""
        val sameName = """{"items":[{"name":"ノート","price":300,"stock":3,"status":"published"},{"name":"ノート","price":400}]}"""

        run("seed", "all", "--dir", dirWith(unknownMember, twoItems)).exitCode shouldBe 5
        val result = run("seed", "all", "--dir", dirWith(ambiguous, sameName), "--json")
        result.exitCode shouldBe 5
        result.json["error"]!!.jsonObject.str("message") shouldContain "2 件"
        run("seed", "staff").exitCode shouldBe 0

        bootstrapDone()
        run("seed", "order", "--dir", defs("order" to ambiguous), "--actor", "S-000001").exitCode shouldBe 5
        total("order", "list") shouldBe 0
    }

    // seed all が何も書かなかったので、ここで初めて管理者がいる
    private fun bootstrapDone() = total("staff", "list") shouldBe 7

    @Test
    fun `seed user などは --actor が無ければ 2、権限が無ければ何も書かずに 7`() {
        bootstrap()
        val reader = run(
            "staff", "create", "--email", "r@example.com", "--name", "読むだけ", "--permission", "member:read", "--password-stdin",
            "--actor", "S-000001", stdin = "correct horse\n",
        )
        reader.exitCode shouldBe 0

        run("seed", "user").exitCode shouldBe 2
        val forbidden = run("seed", "user", "--actor", "S-000002", "--json")
        forbidden.exitCode shouldBe 7
        forbidden.errorCode() shouldBe "FORBIDDEN"
        run("seed", "item", "--actor", "S-000002").exitCode shouldBe 7
        run("seed", "user", "--actor", "M-000001").exitCode shouldBe 2
        run("seed", "user", "--actor", "S-000099").exitCode shouldBe 7
        total("user", "list") shouldBe 0
        total("item", "list") shouldBe 0
    }

    @Test
    fun `途中で core が失敗したら、位置と書いた分が残ることと db reset の案内を出し、core の終了コードで止まる`() {
        bootstrap()
        val users = """{"users":[
            {"email":"a@example.com","name":"会員 A","password":"demo-pass-901"},
            {"email":"b@example.com","name":"会員 B","password":"demo-pass-902"},
            {"email":"not-an-email","name":"会員 C","password":"demo-pass-903"}]}"""

        val result = run("seed", "user", "--dir", defs("user" to users), "--actor", "S-000001", "--json")

        result.exitCode shouldBe 5
        val message = result.json["error"]!!.jsonObject.str("message")
        message shouldContain "user.json の 3 件目"
        message shouldContain "書いた分は残っています"
        message shouldContain "db reset --yes"
        total("user", "list") shouldBe 2
    }

    @Test
    fun `在庫不足・遷移に合わない操作・過去の公開終了は core が止める`() {
        bootstrap()
        run("seed", "user", "--dir", defs("user" to oneUser), "--actor", "S-000001").exitCode shouldBe 0
        run("seed", "item", "--dir", defs("item" to twoItems), "--actor", "S-000001").exitCode shouldBe 0
        val shortage = """{"orders":[
            {"member":"a@example.com","lines":[{"item":"ノート","quantity":2}]},
            {"member":"a@example.com","lines":[{"item":"ノート","quantity":2}]}]}"""

        val stockResult = run("seed", "order", "--dir", defs("order" to shortage), "--actor", "S-000001")
        stockResult.exitCode shouldBe 4
        stockResult.err shouldContain "order.json の 2 件目"
        total("order", "list") shouldBe 1

        run("db", "reset", "--yes").exitCode shouldBe 0
        bootstrap()
        run("seed", "user", "--dir", defs("user" to oneUser), "--actor", "S-000001").exitCode shouldBe 0
        run("seed", "item", "--dir", defs("item" to twoItems), "--actor", "S-000001").exitCode shouldBe 0
        val badSteps = """{"orders":[{"member":"a@example.com","lines":[{"item":"ペン","quantity":1}],"steps":[{"op":"complete"},{"op":"request-cancel"}]}]}"""
        run("seed", "order", "--dir", defs("order" to badSteps), "--actor", "S-000001").exitCode shouldBe 4
        run("order", "get", "O-000001", "--actor", "S-000001", "--json").data.str("status") shouldBe "completed"

        val pastEnd = """{"notices":[{"title":"t","body":"b","start":"-P2D","end":"-P1D"}]}"""
        val noticeResult = run("seed", "notice", "--dir", defs("notice" to pastEnd), "--actor", "S-000001")
        noticeResult.exitCode shouldBe 5
        noticeResult.err shouldContain "notice.json の 1 件目"
        noticeResult.err shouldContain "まだ何も書いていません"
    }

    @Test
    fun `人向けの表示はモジュールごとに件数と番号の範囲を 1 行で出す`() {
        val result = run("seed", "all")

        result.exitCode shouldBe 0
        result.out.trim().lines() shouldContainExactly listOf(
            "staff: 7 件（S-000001〜S-000007）",
            "user: 10 件（M-000001〜M-000010）",
            "item: 8 件（I-000001〜I-000008）",
            "order: 12 件（O-000001〜O-000012）",
            "notice: 6 件（N-000001〜N-000006）",
        )
    }

    @Test
    fun `core db が無ければ 3`() {
        cli("seed", "all", env = env + ("CORE_DB_PATH" to dir.resolve("none.db").toString())).exitCode shouldBe 3
    }
}
