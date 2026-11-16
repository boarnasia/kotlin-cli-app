package io.github.boarnasia.cli.staff

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import io.github.boarnasia.cli.CliIo
import io.github.boarnasia.cli.CoreCommand
import io.github.boarnasia.cli.DbCommand
import io.github.boarnasia.cli.GroupCommand
import io.github.boarnasia.cli.NO_ID_GENERATOR
import io.github.boarnasia.cli.PasswordInput
import io.github.boarnasia.cli.StaffActorCommand
import io.github.boarnasia.cli.statusLabel
import io.github.boarnasia.core.account.PageRange
import io.github.boarnasia.core.db.CoreDb
import io.github.boarnasia.core.db.id.EntityRef
import io.github.boarnasia.core.db.id.IdGenerator
import io.github.boarnasia.core.password.Argon2idPasswordHasher
import io.github.boarnasia.core.staff.Permission
import io.github.boarnasia.core.staff.StaffQuery
import io.github.boarnasia.core.staff.StaffService
import io.github.boarnasia.core.staff.StaffStatus

fun staffCommand(io: CliIo) = GroupCommand("staff", "管理者を管理する").subcommands(
    StaffBootstrapCommand(io),
    StaffCreateCommand(io),
    StaffListCommand(io),
    StaffGetCommand(io),
    StaffUpdateCommand(io),
    StaffSuspendCommand(io),
    StaffActivateCommand(io),
    StaffSetPasswordCommand(io),
    StaffAuthenticateCommand(io),
)

private const val REF_HELP = "管理者の内部 ID か表示用の番号（例 S-000001）"

private val PERMISSION_CHOICES = Permission.entries.associateBy { it.code }
private val PERMISSION_HELP = Permission.entries.joinToString(", ") { it.code }

private fun staffs(db: CoreDb, ids: IdGenerator = NO_ID_GENERATOR) = StaffService(db, ids, Argon2idPasswordHasher())

private fun DbCommand.staffRef() = argument("staff", help = REF_HELP).convert { raw ->
    runCatching { EntityRef.parse(raw) }.getOrElse { fail(it.message ?: "管理者の指定が正しくありません") }
}

private fun CoreCommand.permissionOption(name: String, help: String) =
    option(name, help = "$help（繰り返し指定できる。$PERMISSION_HELP）").choice(PERMISSION_CHOICES).multiple()

class StaffBootstrapCommand(io: CliIo) : DbCommand("bootstrap", "管理者がいないときだけ、全ての権限を持つ最初の管理者を作る", io) {
    private val email by option("--email", help = "メールアドレス").required()
    private val name by option("--name", help = "名前").required()
    private val passwordStdin by option("--password-stdin", help = "パスワードを標準入力の 1 行目から読む").flag()

    override fun execute() {
        val ids = idGenerator()
        val password = PasswordInput.read(io, passwordStdin, confirm = true)
        val db = openExistingDb()
        val staff = staffs(db, ids).bootstrap(email, name, password)
        respond(db, StaffJson.serializer(), StaffJson.of(staff), listOf(staff.createdBy, staff.updatedBy), StaffJson::human)
    }
}

class StaffCreateCommand(io: CliIo) : StaffActorCommand("create", "管理者を登録する（staff:admin が要る）", io) {
    private val email by option("--email", help = "メールアドレス").required()
    private val name by option("--name", help = "名前").required()
    private val permissions by permissionOption("--permission", "付ける権限")
    private val passwordStdin by option("--password-stdin", help = "パスワードを標準入力の 1 行目から読む").flag()

    override fun execute() {
        val ids = idGenerator()
        val db = openExistingDb()
        val actor = actor(db)
        val password = PasswordInput.read(io, passwordStdin, confirm = true)
        val staff = staffs(db, ids).register(email, name, password, permissions.toSet(), actor)
        respond(db, StaffJson.serializer(), StaffJson.of(staff), listOf(staff.createdBy, staff.updatedBy), StaffJson::human)
    }
}

class StaffListCommand(io: CliIo) : StaffActorCommand("list", "管理者の一覧を表示する（表示用の番号の昇順。staff:admin が要る）", io) {
    private val status by option("--status", help = "状態で絞り込む").choice("active", "suspended")
    private val query by option("--query", help = "メールアドレスか名前の部分一致で絞り込む")
    private val limit by option("--limit", help = "件数（1〜${PageRange.MAX_LIMIT}）").int().default(PageRange.DEFAULT_LIMIT)
    private val offset by option("--offset", help = "読み飛ばす件数").int().default(0)

    override fun execute() {
        val db = openExistingDb()
        val page = staffs(db).list(
            StaffQuery(status = status?.let(StaffStatus::ofDbValue), text = query, limit = limit, offset = offset),
            actor(db),
        )
        val data = StaffListJson(page.items.map(StaffJson::of), page.total, page.limit, page.offset)
        respond(StaffListJson.serializer(), data) { list ->
            val rows = list.items.joinToString("\n") {
                "${it.displayNumber}  ${statusLabel(it.status)}  ${it.email}  ${it.name}  ${it.permissionsLabel()}"
            }
            val range = if (list.items.isEmpty()) "" else "（${list.offset + 1}〜${list.offset + list.items.size} 件目）"
            listOf(rows, "全 ${list.total} 件$range").filter { it.isNotEmpty() }.joinToString("\n")
        }
    }
}

class StaffGetCommand(io: CliIo) : StaffActorCommand("get", "管理者を 1 人表示する（自分以外は staff:admin が要る）", io) {
    private val ref by staffRef()

    override fun execute() {
        val db = openExistingDb()
        val staff = staffs(db).get(ref, actor(db))
        respond(db, StaffJson.serializer(), StaffJson.of(staff), listOf(staff.createdBy, staff.updatedBy), StaffJson::human)
    }
}

class StaffUpdateCommand(io: CliIo) : StaffActorCommand("update", "管理者のメールアドレス・名前・権限を変える", io) {
    private val ref by staffRef()
    private val email by option("--email", help = "新しいメールアドレス")
    private val name by option("--name", help = "新しい名前")
    private val grant by permissionOption("--grant", "付ける権限")
    private val revoke by permissionOption("--revoke", "外す権限")

    override fun execute() {
        val db = openExistingDb()
        val staff = staffs(db).update(ref, email, name, grant.toSet(), revoke.toSet(), actor(db))
        respond(db, StaffJson.serializer(), StaffJson.of(staff), listOf(staff.createdBy, staff.updatedBy), StaffJson::human)
    }
}

class StaffSuspendCommand(io: CliIo) : StaffActorCommand("suspend", "有効な管理者を停止する（staff:admin が要る）", io) {
    private val ref by staffRef()
    private val reason by option("--reason", help = "停止の理由（500 文字以内）")

    override fun execute() {
        val db = openExistingDb()
        val staff = staffs(db).suspend(ref, reason, actor(db))
        respond(db, StaffJson.serializer(), StaffJson.of(staff), listOf(staff.createdBy, staff.updatedBy), StaffJson::human)
    }
}

class StaffActivateCommand(io: CliIo) : StaffActorCommand("activate", "停止中の管理者を再開する（staff:admin が要る）", io) {
    private val ref by staffRef()

    override fun execute() {
        val db = openExistingDb()
        val staff = staffs(db).activate(ref, actor(db))
        respond(db, StaffJson.serializer(), StaffJson.of(staff), listOf(staff.createdBy, staff.updatedBy), StaffJson::human)
    }
}

class StaffSetPasswordCommand(io: CliIo) : StaffActorCommand("set-password", "管理者のパスワードを変える（自分以外は staff:admin が要る）", io) {
    private val ref by staffRef()
    private val passwordStdin by option("--password-stdin", help = "パスワードを標準入力の 1 行目から読む").flag()

    override fun execute() {
        val db = openExistingDb()
        val actor = actor(db)
        val password = PasswordInput.read(io, passwordStdin, confirm = true)
        val staff = staffs(db).setPassword(ref, password, actor)
        respond(db, StaffJson.serializer(), StaffJson.of(staff), listOf(staff.createdBy, staff.updatedBy), StaffJson::human)
    }
}

class StaffAuthenticateCommand(io: CliIo) : DbCommand("authenticate", "管理者のメールアドレスとパスワードを照合する", io) {
    private val email by option("--email", help = "メールアドレス").required()
    private val passwordStdin by option("--password-stdin", help = "パスワードを標準入力の 1 行目から読む").flag()

    override fun execute() {
        val password = PasswordInput.read(io, passwordStdin, confirm = false)
        val staff = staffs(openExistingDb()).authenticate(email, password)
        respond(StaffAuthenticateJson.serializer(), StaffAuthenticateJson(authenticated = true, staff = StaffJson.of(staff))) {
            "認証に成功しました: ${it.staff.displayNumber} ${it.staff.email}（権限: ${it.staff.permissionsLabel()}）"
        }
    }
}
