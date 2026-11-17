package io.github.boarnasia.cli.seed

import io.github.boarnasia.cli.CliFailure
import io.github.boarnasia.cli.ErrorCode
import io.github.boarnasia.core.db.id.DisplayNumberKind
import io.github.boarnasia.core.staff.Permission
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.format.DateTimeParseException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 投入の順番どおりに並べる。会員の登録に管理者が、注文に会員と商品が要るため。 */
enum class SeedModule(val key: String, val kind: DisplayNumberKind) {
    STAFF("staff", DisplayNumberKind.STAFF),
    USER("user", DisplayNumberKind.MEMBER),
    ITEM("item", DisplayNumberKind.ITEM),
    ORDER("order", DisplayNumberKind.ORDER),
    NOTICE("notice", DisplayNumberKind.NOTICE),
    ;

    val fileName: String get() = "$key.json"
}

@Serializable
data class StaffSeedFile(val bootstrap: SeedAccount, val staff: List<SeedStaff> = emptyList())

@Serializable
data class SeedAccount(val email: String, val name: String, val password: String)

@Serializable
enum class SeedAccountStatus {
    @SerialName("active") ACTIVE,
    @SerialName("suspended") SUSPENDED,
}

@Serializable
data class SeedStaff(
    val email: String,
    val name: String,
    val password: String,
    val permissions: List<String>,
    val status: SeedAccountStatus = SeedAccountStatus.ACTIVE,
    val suspendReason: String? = null,
)

@Serializable
data class UserSeedFile(val users: List<SeedUser>)

@Serializable
data class SeedUser(
    val email: String,
    val name: String,
    val password: String,
    val status: SeedAccountStatus = SeedAccountStatus.ACTIVE,
    val suspendReason: String? = null,
)

@Serializable
data class ItemSeedFile(val items: List<SeedItem>)

@Serializable
enum class SeedItemStatus {
    @SerialName("unpublished") UNPUBLISHED,
    @SerialName("published") PUBLISHED,
    @SerialName("deleted") DELETED,
}

@Serializable
data class SeedItem(
    val name: String,
    val description: String? = null,
    val price: Long,
    val stock: Long = 0,
    val status: SeedItemStatus = SeedItemStatus.UNPUBLISHED,
)

@Serializable
data class OrderSeedFile(val orders: List<SeedOrder>)

@Serializable
data class SeedOrder(val member: String, val lines: List<SeedOrderLine>, val steps: List<SeedOrderStep> = emptyList())

@Serializable
data class SeedOrderLine(val item: String, val quantity: Long)

@Serializable
enum class SeedOrderOp {
    @SerialName("request-cancel") REQUEST_CANCEL,
    @SerialName("reject-cancel") REJECT_CANCEL,
    @SerialName("approve-cancel") APPROVE_CANCEL,
    @SerialName("complete") COMPLETE,
}

@Serializable
data class SeedOrderStep(val op: SeedOrderOp, val reason: String? = null)

@Serializable
data class NoticeSeedFile(val notices: List<SeedNotice>)

/** start と end は実行した時刻からの ISO-8601 の期間。いつ投入しても予約・公開中・終了の並びが崩れないようにする。 */
@Serializable
data class SeedNotice(
    val title: String,
    val body: String,
    val start: String? = null,
    val end: String? = null,
    val endNow: Boolean = false,
    val deleted: Boolean = false,
)

/** 定義ファイルを読み、形を確かめる。規則（状態の遷移・在庫の数など）は core に任せるので、ここでは見ない。 */
class SeedSource(private val dir: Path?) {

    // 綴りを誤った項目を黙って無視しないよう、知らない項目を誤りにする
    private val json = Json { ignoreUnknownKeys = false }

    fun staff(): StaffSeedFile = load(SeedModule.STAFF, StaffSeedFile.serializer()).also { file ->
        file.staff.forEachIndexed { i, s ->
            s.permissions.forEach { code ->
                Permission.ofCode(code) ?: invalid(SeedModule.STAFF, "staff の ${i + 1} 件目の権限が正しくありません: $code")
            }
        }
    }

    fun users(): UserSeedFile = load(SeedModule.USER, UserSeedFile.serializer())

    fun items(): ItemSeedFile = load(SeedModule.ITEM, ItemSeedFile.serializer())

    fun orders(): OrderSeedFile = load(SeedModule.ORDER, OrderSeedFile.serializer())

    fun notices(): NoticeSeedFile = load(SeedModule.NOTICE, NoticeSeedFile.serializer()).also { file ->
        file.notices.forEachIndexed { i, n ->
            listOfNotNull(n.start, n.end).forEach { parseOffset(it) ?: invalid(SeedModule.NOTICE, "${i + 1} 件目の期間の書き方が正しくありません（例 -P3D・PT12H）: $it") }
        }
    }

    private fun <T> load(module: SeedModule, serializer: KSerializer<T>): T {
        val text = if (dir == null) {
            SeedSource::class.java.getResourceAsStream("/seed/${module.fileName}")?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("同梱の定義ファイルがありません: ${module.fileName}")
        } else {
            val path = dir.resolve(module.fileName)
            if (!Files.isRegularFile(path)) invalid(module, "定義ファイルがありません: ${path.toAbsolutePath().normalize()}")
            Files.readString(path)
        }
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: SerializationException) {
            invalid(module, firstLine(e))
        } catch (e: IllegalArgumentException) {
            invalid(module, firstLine(e))
        }
    }

    // kotlinx.serialization の説明は 2 行目以降に JSON の入力全体とライブラリの設定の案内が続くので、理由の 1 行目だけを出す
    private fun firstLine(e: Exception) = e.message?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: "JSON として読めません"

    companion object {
        fun parseOffset(text: String): Duration? = try {
            Duration.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }

        fun invalid(module: SeedModule, message: String): Nothing = throw CliFailure(ErrorCode.VALIDATION, "${module.fileName}: $message")
    }
}
