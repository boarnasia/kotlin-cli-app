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
```

## 決めごと

- 全てのコマンドが `--json` と終了コードを持つ。形は [docs/json-contract.md](docs/json-contract.md)（dashboard が読む契約）
- DB のパスは `--db` か環境変数 `CORE_DB_PATH`
- カレントディレクトリの `.env` を読む。同じ名前の環境変数があればそちらが優先される
- `db`・`user authenticate`・`staff authenticate`・`staff bootstrap` を除く全てのコマンドは、閲覧を含めて `--actor`（操作した管理者の内部 ID か S- の番号）を必須で取る。いて有効で権限を持つかは core のユースケースが確かめ、だめなら終了コード 7（FORBIDDEN）
- 権限は 8 種類（`member:read`・`member:write`・`order:read`・`order:write`・`order:cancel-approve`・`item:write`・`notice:write`・`staff:admin`）。最初の管理者は `staff bootstrap` で作る
- 人向けの表示の時刻だけ JST。`--json` は UTC
- 人向けの表示では、操作した人を番号で出す（「管理者 S-000001」）。`--json` の `createdBy`・`updatedBy` は内部 ID のまま
- 商品と在庫は、権限を問わず有効な管理者なら見られる。登録・変更・公開・削除と在庫の調整は `item:write`
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
| `order` / `notice` / `seed` | これから（seed の順番は staff → user → item → order → notice） |
