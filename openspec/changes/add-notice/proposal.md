# Proposal

## Why

管理者が CLI からお知らせを下書き・予約・公開・終了・削除でき、ある時刻に何が公開中かを確かめられるようにする。規則は kotlin-core-app の change `add-notice`（core-notice）が持つ。

## What Changes

- `notice create|list|get|update|delete|end|visible` を足す
- 日時は ISO-8601 で受け取り、時差の無い入力は日本時間として読む
- お知らせの --json の形を `docs/json-contract.md` に足す

## Capabilities

### New Capabilities

- `cli-notice-commands`: notice の各コマンドの入力・出力・失敗と終了コード・日時の入力の形

### Modified Capabilities

なし

## Impact

- 影響するコード: `apps/cli` に notice のコマンドを追加
- 契約: data の形の追加だけで、contractVersion は 1 のまま
