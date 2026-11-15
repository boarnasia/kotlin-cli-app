# Proposal

## Why

手順 1 で CLI（core-cli）の骨組みと db コマンドを実装したが、全コマンドで守る決まりと、dashboard が読む --json の契約は README と docs にしか無く、仕様としては残っていない。user 以降のコマンドを足すときに形がぶれないよう、今の振る舞いを OpenSpec の仕様として固定する。

## What Changes

- 実装済みの振る舞いを、新しい仕様として記録する。コードは変えない
- CLI の共通の決まり: DB の指定（--db・CORE_DB_PATH）、.env の読み込み、--actor、人向けの表示
- --json の契約: 出力の形、値の表し方、失敗の種類と終了コード
- db migrate・db status・db reset の振る舞い

## Capabilities

### New Capabilities

- `cli-conventions`: 全コマンドに共通する入力の決まり（DB の指定・.env・--actor）と人向けの出力
- `cli-json-contract`: --json の出力の形と、失敗の種類ごとの終了コード。dashboard が読む契約
- `cli-db-commands`: core.db のスキーマを管理する db migrate・db status・db reset

### Modified Capabilities

（なし）

## Impact

- 影響するコード: なし（`apps/cli` の現在の振る舞いを記録するだけ）
- dashboard は cli-json-contract に従って出力を読む
- 以降のコマンド（user・staff・item・stock・order・notice・seed）はこの仕様に従う
- 関連する文書: `README.md`、`docs/json-contract.md`
- core.db そのものの振る舞いは kotlin-core-app の仕様（core-db・core-ids・core-time-and-money）に従う
