# Proposal

## Why

管理者が会員を CLI から登録・確認・停止でき、dashboard が会員の情報を --json で読めるようにする。会員の規則は kotlin-core-app の change `add-user`（core-user・core-password）が持ち、CLI はそれを呼ぶ薄い層にする。

## What Changes

- `user create|list|get|update|suspend|activate|set-password|authenticate` を足す
- 会員の --json の形（data）を決め、`docs/json-contract.md` に足す。登録と最終変更をした人は、管理者か会員本人かが分かる形で出す
- パスワードを引数の値で受け取らない方法（標準入力か、表示しない入力）を足す
- 内部 ID を作るために、CLI の app-id（CORE_APP_ID）を読む

## Capabilities

### New Capabilities

- `cli-user-commands`: user の各コマンドの入力・出力（--json の data）・失敗と終了コード

### Modified Capabilities

（なし。--db・--actor・--json の外側の形・終了コードは既存の cli-conventions・cli-json-contract に従う）

## Impact

- 影響するコード: `apps/cli` に user のコマンドを追加
- 依存: kotlin-core-app の `lib/core/user`（composite build）
- 文書: `docs/json-contract.md`、`README.md` のコマンドの表
- dashboard が読む契約に、会員の data の形が加わる（contractVersion は 1 のまま。項目の追加にあたる）
