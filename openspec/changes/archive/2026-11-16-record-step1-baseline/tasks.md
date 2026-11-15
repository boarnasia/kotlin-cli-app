# Tasks

手順 1 で実装済みの作業を、確かめた方法とともに記録する。

## 1. ビルドの骨組み

- [x] 1.1 Gradle の wrapper・version catalog・build-logic の convention plugin を置き、composite build で kotlin-core-app を取り込んで `./gradlew test` が通ることを確かめる
- [x] 1.2 `./gradlew :apps:cli:installDist` で core-cli を作り、`core-cli --help` が動くことを確かめる

## 2. 共通の決まり

- [x] 2.1 --db と CORE_DB_PATH を実装し、環境変数だけで動く `DbCommandsTest` と、両方を指定すると --db が使われることを core-cli で確かめる
- [x] 2.2 .env の読み込みを実装し、`DotEnvTest`（コメント・引用符・環境変数の優先・ファイルが無いとき）が通ることを確かめる
- [x] 2.3 人向けの表示を実装し、時刻が JST で出て、失敗が標準エラーだけに出る `DbCommandsTest` が通ることを確かめる
- [x] 2.4 app-id と DB のパスの例を `.env.example` に、--actor の決まりを `README.md` に書く

## 3. --json の契約

- [x] 3.1 成功と失敗の外側の形を実装し、`DbCommandsTest` で contractVersion・ok・command・data の項目が決めたとおりであることを確かめる
- [x] 3.2 引数の誤りを --json で返す処理を実装し、--db が無いときと知らないコマンドの `DbCommandsTest` が通ることを確かめる
- [x] 3.3 終了コードを実装し、DB_NOT_FOUND が 3、USAGE が 2 になる `DbCommandsTest` が通ることを確かめる
- [x] 3.4 契約と終了コードの表を `docs/json-contract.md` に書く

## 4. db コマンド

- [x] 4.1 db migrate を実装し、新しい DB を作る `DbCommandsTest` と、ディレクトリが無いと終了コード 3 で何も作らないことを core-cli で確かめる
- [x] 4.2 db status を実装し、適用後の状況と、core.db が無いとファイルを作らずに失敗する `DbCommandsTest` が通ることを確かめる
- [x] 4.3 db reset を実装し、--yes が無いと終了コード 2、あると適用し直す `DbCommandsTest` が通ることを確かめる

## Workflow follow-up

- `openspec validate --strict` を通してから archive し、`openspec/specs/` に仕様が反映されたことを確かめる
