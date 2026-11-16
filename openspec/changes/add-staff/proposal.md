# Proposal

## Why

管理者を CLI から作って管理し、`--actor` に渡された管理者が実在し、有効で、権限を持つかを確かめられるようにする。規則は kotlin-core-app の change `add-staff`（core-staff・core-authorization）が持ち、CLI はそれを呼ぶ。

## What Changes

- `staff bootstrap|create|list|get|update|suspend|activate|set-password|authenticate` を足す。`bootstrap` は最初の管理者を作る（ハンドオフのコマンドの一覧には無い。kotlin-core-app の design.md の Open Questions 1 で決まった）
- `--actor` を、管理者の内部 ID に加えて S- の番号でも受け付ける。いない・停止中・権限が無い管理者は FORBIDDEN（終了コード 7）で拒否する
- 閲覧のコマンド（`user get`・`user list`・`staff get`・`staff list`）にも `--actor` を必須にする
- 管理者の --json の形（data）を決め、`docs/json-contract.md` に足す
- 終了コードの表に FORBIDDEN（7）を足す
- error.code と終了コードは今後増えることがあり、呼び出し側は知らないコードを失敗として扱う、と契約に書き足す

## Capabilities

### New Capabilities

- `cli-staff-commands`: staff の各コマンドの入力・出力（--json の data）・失敗と終了コード

### Modified Capabilities

- `cli-conventions`: `--actor` の受け付け方（S- の番号、閲覧にも必須、確認と FORBIDDEN）
- `cli-user-commands`: `--actor` の要件（閲覧にも必須、S- の番号、FORBIDDEN）
- `cli-json-contract`: 終了コードの表に FORBIDDEN（7）を足す。error.code と終了コードが増えうることと、知らないコードの扱いを書き足す

## Impact

- 影響するコード: `apps/cli` に staff のコマンドを追加、`--actor` の基底を変更、user の閲覧のコマンドに `--actor` を追加
- dashboard: `user get`・`user list` などの閲覧にも `--actor` を渡す必要がある（**BREAKING**: 今の呼び方では USAGE になる）
- 契約: 終了コード 7 の追加と管理者の data の追加。既存の項目と終了コードは変えないので contractVersion は 1 のまま
- 文書: `docs/json-contract.md`、`README.md`
