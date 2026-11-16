# Proposal

## Why

商品と在庫を CLI から登録・確認・調整できるようにし、dashboard がその結果を --json で読めるようにする。規則は kotlin-core-app の change `add-item`（core-item・core-stock）が持ち、CLI はそれを呼ぶ。

## What Changes

- `item create|list|get|update|delete|publish|unpublish` を足す
- `stock show|adjust|history` を足す
- 商品・在庫・在庫の変動の --json の形（data）を決め、`docs/json-contract.md` に足す
- どのコマンドも、閲覧を含めて `--actor` を取る（cli-conventions のとおり）。書き込みは item:write、閲覧は有効な管理者なら権限を問わない（kotlin-core-app の design.md の Open Questions 2）

## Capabilities

### New Capabilities

- `cli-item-commands`: item の各コマンドの入力・出力（--json の data）・失敗と終了コード
- `cli-stock-commands`: stock の各コマンドの入力・出力（--json の data）・失敗と終了コード

### Modified Capabilities

なし（`--actor` の受け付け方は cli-conventions、終了コードは cli-json-contract のまま。在庫不足は既存の CONFLICT（4））

## Impact

- 影響するコード: `apps/cli` に item と stock のコマンドを追加
- 契約: data の形の追加だけで、contractVersion は 1 のまま
- 文書: `docs/json-contract.md`、`README.md`
