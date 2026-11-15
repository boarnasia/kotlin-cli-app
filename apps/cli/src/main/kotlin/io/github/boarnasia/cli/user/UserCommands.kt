package io.github.boarnasia.cli.user

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import io.github.boarnasia.cli.CliIo
import io.github.boarnasia.cli.DbCommand
import io.github.boarnasia.cli.GroupCommand
import io.github.boarnasia.cli.PasswordInput
import io.github.boarnasia.cli.WriteCommand
import io.github.boarnasia.core.db.CoreDb
import io.github.boarnasia.core.db.id.EntityRef
import io.github.boarnasia.core.db.id.IdGenerator
import io.github.boarnasia.core.password.Argon2idPasswordHasher
import io.github.boarnasia.core.user.MemberQuery
import io.github.boarnasia.core.user.MemberStatus
import io.github.boarnasia.core.user.Registrant
import io.github.boarnasia.core.user.UserService

fun userCommand(io: CliIo) = GroupCommand("user", "会員を管理する").subcommands(
    UserCreateCommand(io),
    UserListCommand(io),
    UserGetCommand(io),
    UserUpdateCommand(io),
    UserSuspendCommand(io),
    UserActivateCommand(io),
    UserSetPasswordCommand(io),
    UserAuthenticateCommand(io),
)

private const val REF_HELP = "会員の内部 ID・表示用の番号（例 M-000123）・公開 ID（UUID）のどれか"

// 読むだけのコマンドは内部 ID を作らないので、呼ばれたら設定の誤りとして気づけるようにする
private val NO_ID_GENERATOR = IdGenerator { error("読み取りのコマンドで内部 ID を作ろうとしました") }

private fun users(db: CoreDb, ids: IdGenerator = NO_ID_GENERATOR) = UserService(db, ids, Argon2idPasswordHasher())

private fun DbCommand.userRef() = argument("user", help = REF_HELP).convert { raw ->
    runCatching { EntityRef.parse(raw) }.getOrElse { fail(it.message ?: "会員の指定が正しくありません") }
}

class UserCreateCommand(io: CliIo) : WriteCommand("create", "会員を登録する", io) {
    private val email by option("--email", help = "メールアドレス").required()
    private val name by option("--name", help = "名前").required()
    private val passwordStdin by option("--password-stdin", help = "パスワードを標準入力の 1 行目から読む").flag()

    override fun execute() {
        val ids = idGenerator()
        val password = PasswordInput.read(io, passwordStdin, confirm = true)
        val member = users(openExistingDb(), ids).register(email, name, password, Registrant.ByStaff(actor))
        respond(MemberJson.serializer(), MemberJson.of(member), MemberJson::human)
    }
}

class UserListCommand(io: CliIo) : DbCommand("list", "会員の一覧を表示する（表示用の番号の昇順）", io) {
    private val status by option("--status", help = "状態で絞り込む").choice("active", "suspended")
    private val query by option("--query", help = "メールアドレスか名前の部分一致で絞り込む")
    private val limit by option("--limit", help = "件数（1〜${MemberQuery.MAX_LIMIT}）").int().default(MemberQuery.DEFAULT_LIMIT)
    private val offset by option("--offset", help = "読み飛ばす件数").int().default(0)

    override fun execute() {
        val page = users(openExistingDb()).list(
            MemberQuery(status = status?.let(MemberStatus::ofDbValue), text = query, limit = limit, offset = offset),
        )
        val data = MemberListJson(page.items.map(MemberJson::of), page.total, page.limit, page.offset)
        respond(MemberListJson.serializer(), data) { list ->
            val rows = list.items.joinToString("\n") { "${it.displayNumber}  ${statusLabel(it.status)}  ${it.email}  ${it.name}" }
            val range = if (list.items.isEmpty()) "" else "（${list.offset + 1}〜${list.offset + list.items.size} 件目）"
            listOf(rows, "全 ${list.total} 件$range").filter { it.isNotEmpty() }.joinToString("\n")
        }
    }
}

class UserGetCommand(io: CliIo) : DbCommand("get", "会員を 1 人表示する", io) {
    private val ref by userRef()

    override fun execute() {
        respond(MemberJson.serializer(), MemberJson.of(users(openExistingDb()).get(ref)), MemberJson::human)
    }
}

class UserUpdateCommand(io: CliIo) : WriteCommand("update", "会員のメールアドレスか名前を変える", io) {
    private val ref by userRef()
    private val email by option("--email", help = "新しいメールアドレス")
    private val name by option("--name", help = "新しい名前")

    override fun execute() {
        val member = users(openExistingDb()).update(ref, email, name, actor)
        respond(MemberJson.serializer(), MemberJson.of(member), MemberJson::human)
    }
}

class UserSuspendCommand(io: CliIo) : WriteCommand("suspend", "有効な会員を停止する", io) {
    private val ref by userRef()
    private val reason by option("--reason", help = "停止の理由（500 文字以内）")

    override fun execute() {
        val member = users(openExistingDb()).suspend(ref, reason, actor)
        respond(MemberJson.serializer(), MemberJson.of(member), MemberJson::human)
    }
}

class UserActivateCommand(io: CliIo) : WriteCommand("activate", "停止中の会員を再開する", io) {
    private val ref by userRef()

    override fun execute() {
        val member = users(openExistingDb()).activate(ref, actor)
        respond(MemberJson.serializer(), MemberJson.of(member), MemberJson::human)
    }
}

class UserSetPasswordCommand(io: CliIo) : WriteCommand("set-password", "会員のパスワードを変える", io) {
    private val ref by userRef()
    private val passwordStdin by option("--password-stdin", help = "パスワードを標準入力の 1 行目から読む").flag()

    override fun execute() {
        val password = PasswordInput.read(io, passwordStdin, confirm = true)
        val member = users(openExistingDb()).setPassword(ref, password, actor)
        respond(MemberJson.serializer(), MemberJson.of(member), MemberJson::human)
    }
}

class UserAuthenticateCommand(io: CliIo) : DbCommand("authenticate", "会員のメールアドレスとパスワードを照合する", io) {
    private val email by option("--email", help = "メールアドレス").required()
    private val passwordStdin by option("--password-stdin", help = "パスワードを標準入力の 1 行目から読む").flag()

    override fun execute() {
        val password = PasswordInput.read(io, passwordStdin, confirm = false)
        val member = users(openExistingDb()).authenticate(email, password)
        respond(AuthenticateJson.serializer(), AuthenticateJson(authenticated = true, user = MemberJson.of(member))) {
            "認証に成功しました: ${it.user.displayNumber} ${it.user.email}"
        }
    }
}
