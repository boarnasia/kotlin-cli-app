package io.github.boarnasia.cli.notice

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import io.github.boarnasia.cli.CliFailure
import io.github.boarnasia.cli.CliIo
import io.github.boarnasia.cli.ErrorCode
import io.github.boarnasia.cli.GroupCommand
import io.github.boarnasia.cli.NO_ID_GENERATOR
import io.github.boarnasia.cli.StaffActorCommand
import io.github.boarnasia.core.account.PageRange
import io.github.boarnasia.core.db.CoreDb
import io.github.boarnasia.core.db.UtcTimestamps
import io.github.boarnasia.core.db.id.EntityRef
import io.github.boarnasia.core.notice.Notice
import io.github.boarnasia.core.notice.NoticeQuery
import io.github.boarnasia.core.notice.NoticeService
import io.github.boarnasia.core.notice.NoticeStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

fun noticeCommand(io: CliIo) = GroupCommand("notice", "お知らせを管理する").subcommands(
    NoticeCreateCommand(io), NoticeGetCommand(io), NoticeListCommand(io), NoticeUpdateCommand(io),
    NoticeEndCommand(io), NoticeDeleteCommand(io), NoticeVisibleCommand(io),
)

private fun notices(db: CoreDb) = NoticeService(db, NO_ID_GENERATOR)
private fun StaffActorCommand.datetime(name: String) = option(name, help = "ISO-8601 の日時（時差なしは JST）").convert { raw ->
    try {
        try { OffsetDateTime.parse(raw).toInstant() }
        catch (_: java.time.format.DateTimeParseException) { LocalDateTime.parse(raw).atZone(ZoneId.of("Asia/Tokyo")).toInstant() }
    } catch (_: java.time.format.DateTimeParseException) {
        fail("ISO-8601 の日時を指定してください（例 2026-12-01T09:00、2026-12-01T00:00Z）: $raw")
    }
}
private fun StaffActorCommand.noticeRef() = argument("notice", help = "内部 ID か N- の番号").convert { raw ->
    runCatching { EntityRef.parse(raw) }.getOrElse { fail(it.message ?: "お知らせの指定が正しくありません") }
}

abstract class NoticeCommand(name: String, summary: String, io: CliIo) : StaffActorCommand(name, summary, io) {
    protected fun respondNotice(db: CoreDb, n: Notice) =
        respond(db, NoticeJson.serializer(), NoticeJson.of(n), listOf(n.createdBy, n.updatedBy), NoticeJson::human)
}

class NoticeCreateCommand(io: CliIo) : NoticeCommand("create", "お知らせを登録する（notice:write が要る）", io) {
    private val title by option("--title", help = "題名（100 文字以内）").required()
    private val body by option("--body", help = "本文（5000 文字以内）").required()
    private val start by datetime("--start")
    private val end by datetime("--end")
    override fun execute() {
        val ids = idGenerator()
        val db = openExistingDb()
        respondNotice(db, NoticeService(db, ids).create(title, body, start, end, actor(db)))
    }
}
class NoticeGetCommand(io: CliIo) : NoticeCommand("get", "お知らせを表示する（削除済みも含む）", io) {
    private val ref by noticeRef()
    override fun execute() {
        val db = openExistingDb()
        respondNotice(db, notices(db).get(ref, actor(db)))
    }
}
class NoticeListCommand(io: CliIo) : StaffActorCommand("list", "お知らせを番号の新しい順に表示する", io) {
    private val status by option("--status").choice(*NoticeStatus.entries.map { it.dbValue }.toTypedArray())
    private val limit by option("--limit").int().default(PageRange.DEFAULT_LIMIT)
    private val offset by option("--offset").int().default(0)
    override fun execute() {
        val db = openExistingDb()
        val page = notices(db).list(NoticeQuery(status?.let(NoticeStatus::ofDbValue), limit, offset), actor(db))
        val data = NoticeListJson(page.items.map(NoticeJson::of), page.total, page.limit, page.offset)
        respond(NoticeListJson.serializer(), data) {
            val rows = it.items.joinToString("\n", transform = NoticeJson::row)
            val range = if (it.items.isEmpty()) "" else "（${it.offset + 1}〜${it.offset + it.items.size} 件目）"
            listOf(rows, "全 ${it.total} 件$range").filter(String::isNotEmpty).joinToString("\n")
        }
    }
}
class NoticeUpdateCommand(io: CliIo) : NoticeCommand("update", "お知らせを変更する（notice:write が要る）", io) {
    private val ref by noticeRef()
    private val title by option("--title")
    private val body by option("--body")
    private val start by datetime("--start")
    private val end by datetime("--end")
    private val clearStart by option("--clear-start", help = "公開開始を消す").flag()
    private val clearEnd by option("--clear-end", help = "公開終了を消す").flag()
    override fun execute() {
        if ((start != null && clearStart) || (end != null && clearEnd)) throw CliFailure(ErrorCode.USAGE, "同じ日時の指定と消去は同時にできません")
        val db = openExistingDb()
        respondNotice(db, notices(db).update(ref, title, body, start, end, clearStart, clearEnd, actor(db)))
    }
}
class NoticeEndCommand(io: CliIo) : NoticeCommand("end", "公開中のお知らせを今すぐ終了する（notice:write が要る）", io) {
    private val ref by noticeRef()
    override fun execute() {
        val db = openExistingDb()
        respondNotice(db, notices(db).end(ref, actor(db)))
    }
}
class NoticeDeleteCommand(io: CliIo) : NoticeCommand("delete", "お知らせを論理削除する。戻せない（notice:write が要る）", io) {
    private val ref by noticeRef()
    private val yes by option("--yes", help = "お知らせを戻せないことを確認した").flag()
    override fun execute() {
        if (!yes) throw CliFailure(ErrorCode.USAGE, "notice delete はお知らせを戻せない形で削除します。実行するには --yes を付けてください")
        val db = openExistingDb()
        respondNotice(db, notices(db).delete(ref, actor(db)))
    }
}
class NoticeVisibleCommand(io: CliIo) : StaffActorCommand("visible", "指定時刻に公開中のお知らせを表示する", io) {
    private val at by datetime("--at")
    override fun execute() {
        val db = openExistingDb()
        val time = at ?: Instant.now()
        val data = NoticeVisibleJson(UtcTimestamps.format(time), notices(db).visible(time, actor(db)).map {
            // status は契約に合わせて実行時刻で計算し、at は公開の抽出だけに使う
            NoticeJson.of(it.copy(status = NoticeStatus.at(it.publishStartAt, it.publishEndAt, Instant.now())))
        })
        respond(NoticeVisibleJson.serializer(), data) { it.items.joinToString("\n", transform = NoticeJson::row) }
    }
}
