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
```

## 決めごと

- 全てのコマンドが `--json` と終了コードを持つ。形は [docs/json-contract.md](docs/json-contract.md)（dashboard が読む契約）
- DB のパスは `--db` か環境変数 `CORE_DB_PATH`
- カレントディレクトリの `.env` を読む。同じ名前の環境変数があればそちらが優先される
- `db`・`user authenticate`・`staff authenticate`・`staff bootstrap` を除く全てのコマンドは、閲覧を含めて `--actor`（操作した管理者の内部 ID か S- の番号）を必須で取る。いて有効で権限を持つかは core のユースケースが確かめ、だめなら終了コード 7（FORBIDDEN）
- 権限は 7 種類（`member:read`・`member:write`・`order:read`・`order:cancel-approve`・`item:write`・`notice:write`・`staff:admin`）。最初の管理者は `staff bootstrap` で作る
- 人向けの表示の時刻だけ JST。`--json` は UTC
- 内部 ID を作るコマンド（`user create`・`staff bootstrap` など）は `CORE_APP_ID`（CLI は 3）が要る
- パスワードは引数の値では受け取らない。`--password-stdin` で標準入力の 1 行目から読むか、端末で表示しない入力を 2 回求める

## コマンド

| コマンド | 状況 |
| --- | --- |
| `db migrate` / `db status` / `db reset --yes` | 済み |
| `user create` / `list` / `get` / `update` / `suspend` / `activate` / `set-password` / `authenticate` | 済み |
| `staff bootstrap` / `create` / `list` / `get` / `update` / `suspend` / `activate` / `set-password` / `authenticate` | 済み |
| `item` / `stock` / `order` / `notice` / `seed` | これから（seed の順番は staff → user → item → order → notice） |
