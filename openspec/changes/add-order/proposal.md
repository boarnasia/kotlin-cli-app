# Proposal

## Why

管理者が CLI から会員の代わりに注文し、キャンセルの申請を承認・却下し、注文を完了できるようにし、dashboard がその結果を --json で読めるようにする。seed とテストで注文を作る入口にもなる。規則は kotlin-core-app の change `add-order`（core-order）が持ち、CLI はそれを呼ぶ。

## What Changes

- `order place|list|get|request-cancel|approve-cancel|reject-cancel|complete` を足す
- 注文（明細と履歴を含む）の --json の形（data）を決め、`docs/json-contract.md` に足す
- どのコマンドも、閲覧を含めて `--actor` を取る（cli-conventions のとおり）。閲覧は order:read、代わりの注文・代わりのキャンセルの申請・完了は order:write、承認と却下は order:cancel-approve
- staff の権限の選択肢と data の permissions に `order:write` が増え、8 種類になる（kotlin-core-app の design.md の Open Questions 1）

## Capabilities

### New Capabilities

- `cli-order-commands`: order の各コマンドの入力・出力（--json の data）・失敗と終了コード・人向けの表示

### Modified Capabilities

- `cli-staff-commands`: 管理者の data の permissions を 8 種類の決めた順にする

## Impact

- 影響するコード: `apps/cli` に order のコマンドを追加。staff のコマンドは core の権限の enum から選択肢を作っているので、コードの変更は無い
- 契約: data の形の追加と、permissions に入りうる値の追加だけで、contractVersion は 1 のまま
- 文書: `docs/json-contract.md`、`README.md`
