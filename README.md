# kotlin-cli-app

core.db を操作する管理用の CLI（`core-cli`）の試作。ドメインと DB は [kotlin-core-app](../kotlin-core-app) が持ち、composite build（`includeBuild("../kotlin-core-app")`）で取り込む。2 つのリポジトリを同じ階層に置く。

## 必要なもの

- Gradle を起動するための JDK 17 以上（`JAVA_HOME` で指定）。ビルドとデーモンは JDK 25 で動き、無ければ Gradle が取得する

## 動かす

```sh
./gradlew test
./gradlew :apps:cli:installDist
cp .env.example .env

apps/cli/build/install/core-cli/bin/core-cli db migrate
apps/cli/build/install/core-cli/bin/core-cli db status --json
# 最初の管理者（S-000001、全ての権限）を作る。管理者がいないときだけ動く
apps/cli/build/install/core-cli/bin/core-cli staff bootstrap --email admin@example.com --name 管理者
apps/cli/build/install/core-cli/bin/core-cli user list --actor S-000001

# 商品を登録し、入荷して公開する
apps/cli/build/install/core-cli/bin/core-cli item create --name ノート --price 300 --actor S-000001
apps/cli/build/install/core-cli/bin/core-cli stock adjust I-000001 --delta 10 --reason 入荷 --actor S-000001
apps/cli/build/install/core-cli/bin/core-cli item publish I-000001 --actor S-000001

# 会員の代わりに注文し、完了する
apps/cli/build/install/core-cli/bin/core-cli order place --user M-000001 --item I-000001:2 --actor S-000001
apps/cli/build/install/core-cli/bin/core-cli order complete O-000001 --actor S-000001
```

## 決めごと

- 全てのコマンドが `--json` と終了コードを持つ。形は [docs/json-contract.md](docs/json-contract.md)（dashboard が読む契約）
- DB のパスは `--db` か環境変数 `CORE_DB_PATH`
- カレントディレクトリの `.env` を読む。同じ名前の環境変数があればそちらが優先される
- `db`・`user authenticate`・`staff authenticate`・`staff bootstrap`・`seed staff`・`seed all` を除く全てのコマンドは、閲覧を含めて `--actor`（操作した管理者の内部 ID か S- の番号）を必須で取る。いて有効で権限を持つかは core のユースケースが確かめ、だめなら終了コード 7（FORBIDDEN）
- 権限は 8 種類（`member:read`・`member:write`・`order:read`・`order:write`・`order:cancel-approve`・`item:write`・`notice:write`・`staff:admin`）。最初の管理者は `staff bootstrap` で作る
- 人向けの表示の時刻だけ JST。`--json` は UTC
- 人向けの表示では、操作した人を番号で出す（「管理者 S-000001」）。`--json` の `createdBy`・`updatedBy` は内部 ID のまま
- 商品と在庫は、権限を問わず有効な管理者なら見られる。登録・変更・公開・削除と在庫の調整は `item:write`
- 注文の閲覧は `order:read`、会員の代わりの注文・代わりのキャンセルの申請・完了は `order:write`、キャンセルの承認と却下は `order:cancel-approve`
- 注文の明細は `--item 商品:数` を繰り返す（`order place --user M-000001 --item I-000001:2 --item I-000002:1 --actor S-000001`）
- 在庫を減らすときは負の数を渡す（`stock adjust I-000001 --delta -3 --reason 棚卸し`。`--delta=-3` も可）
- 内部 ID を作るコマンド（`user create`・`staff bootstrap` など）は `CORE_APP_ID`（CLI は 3）が要る
- パスワードは引数の値では受け取らない。`--password-stdin` で標準入力の 1 行目から読むか、端末で表示しない入力を 2 回求める

## コマンド

| コマンド | 状況 |
| --- | --- |
| `db migrate` / `db status` / `db reset --yes` | 済み |
| `user create` / `list` / `get` / `update` / `suspend` / `activate` / `set-password` / `authenticate` | 済み |
| `staff bootstrap` / `create` / `list` / `get` / `update` / `suspend` / `activate` / `set-password` / `authenticate` | 済み |
| `item create` / `list` / `get` / `update` / `publish` / `unpublish` / `delete --yes` | 済み |
| `stock show` / `adjust` / `history` | 済み |
| `order place` / `list` / `get` / `request-cancel` / `approve-cancel` / `reject-cancel --reason` / `complete` | 済み |
| `notice create` / `list` / `get` / `update` / `end` / `delete --yes` / `visible` | 済み |
| `seed all` / `staff` / `user` / `item` / `order` / `notice` | 済み（staff → user → item → order → notice の順） |

## お知らせ

```sh
apps/cli/build/install/core-cli/bin/core-cli notice create --title お休み --body 年末年始 --actor S-000001
apps/cli/build/install/core-cli/bin/core-cli notice update N-000001 --start 2026-12-01T09:00 --end 2026-12-31T18:00 --actor S-000001
apps/cli/build/install/core-cli/bin/core-cli notice visible --at 2026-12-01T09:00 --actor S-000001 --json
apps/cli/build/install/core-cli/bin/core-cli notice list --status scheduled --actor S-000001
```

日時は ISO-8601。時差が無ければ JST として読み、日付だけは受け付けない。公開終了は公開開始より後かつ設定時点より後にする。`--clear-start`・`--clear-end` で日時を消す。同じ項目の設定と消去は同時に指定できない。

公開中は題名・本文・公開終了を変更できる。`end` は公開中だけを今すぐ終了する。`delete --yes` は公開中を含めどの状態からも論理削除する。閲覧は有効な管理者なら許し、書き込みは `notice:write` が要る。

## seed（決まった内容の投入）

```sh
# 新しいディレクトリで始めるとき
cp .env.example .env
apps/cli/build/install/core-cli/bin/core-cli db migrate
apps/cli/build/install/core-cli/bin/core-cli seed all

# やり直すとき
apps/cli/build/install/core-cli/bin/core-cli db reset --yes
apps/cli/build/install/core-cli/bin/core-cli seed all

# 別の定義ファイルで 1 つのモジュールだけ投入する
apps/cli/build/install/core-cli/bin/core-cli seed item --dir ./my-seed --actor S-000001
```

- `db reset --yes` は既にある core.db を作り直すコマンドで、core.db が無いと終了コード 3（DB_NOT_FOUND）になる。新しいディレクトリでは先に `db migrate` を実行する
- 既定の定義は CLI に同梱している（`apps/cli/src/main/resources/seed/`）。管理者 7 人・会員 10 人・商品 8 件・注文 12 件・お知らせ 6 件で、注文とお知らせは各状態を含む
- `--dir` を付けると、そのディレクトリの `staff.json`・`user.json`・`item.json`・`order.json`・`notice.json` を読む（`seed all` は 5 つとも要る）
- データが 1 件でもあるモジュールには投入しない（終了コード 4）。やり直すときは `db reset --yes` で作り直す
- `seed staff` と `seed all` は `--actor` を取らず、最初の管理者を `staff bootstrap` と同じ方法で作り、その管理者が残りを投入する。`seed user|item|order|notice` は `--actor` が要り、権限は各コマンドと同じ（`seed order` は `order:write` と、却下・承認があれば `order:cancel-approve`）
- 書く前に確かめるのは、定義ファイルの形と、注文の参照（会員のメールアドレス・商品名）だけ。状態の遷移・在庫の数・日時などの規則は core が確かめ、途中で失敗したら書いた分は残る（エラーに何件目かが出る）
- 定義ファイルのパスワードは平文。リポジトリは公開しているので、ほかで使わないデモ用の値（`demo-pass-001` など）だけを書き、実在の人やパスワードは書かない
- 同梱の管理者は `admin@example.com`（`demo-pass-001`、全ての権限）など、会員は `member01@example.com`〜`member10@example.com`（`demo-pass-101`〜`demo-pass-110`）

定義ファイルの形:

| ファイル | 中身 |
| --- | --- |
| `staff.json` | `bootstrap`（`email`・`name`・`password`）と `staff` の並び（`email`・`name`・`password`・`permissions`・`status`（`active`/`suspended`）・`suspendReason`） |
| `user.json` | `users` の並び（`email`・`name`・`password`・`status`・`suspendReason`） |
| `item.json` | `items` の並び（`name`・`description`・`price`・`stock`（初期在庫、理由「初期在庫」で調整）・`status`（`unpublished`/`published`/`deleted`）） |
| `order.json` | `orders` の並び（`member`（メールアドレス）・`lines`（`item`（商品名）・`quantity`）・`steps`（`op`: `request-cancel`/`reject-cancel`/`approve-cancel`/`complete`、`reason`）） |
| `notice.json` | `notices` の並び（`title`・`body`・`start`・`end`（実行した時刻からの ISO-8601 の期間、例 `-P3D`・`PT12H`）・`endNow`・`deleted`） |

知らない項目は誤り（終了コード 5）になる。時刻は全て実行した時刻で、過去の日付への投入はしない。
