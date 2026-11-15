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
```

## 決めごと

- 全てのコマンドが `--json` と終了コードを持つ。形は [docs/json-contract.md](docs/json-contract.md)（dashboard が読む契約）
- DB のパスは `--db` か環境変数 `CORE_DB_PATH`
- カレントディレクトリの `.env` を読む。同じ名前の環境変数があればそちらが優先される
- 書き込みのコマンドは `--actor`（操作した管理者の ID）を取る。`db` のコマンドは管理者の表が無くても動く必要があるので取らない
- 人向けの表示の時刻だけ JST。`--json` は UTC

## コマンド

| コマンド | 状況 |
| --- | --- |
| `db migrate` / `db status` / `db reset --yes` | 済み |
| `user` / `staff` / `item` / `stock` / `order` / `notice` / `seed` | これから |
