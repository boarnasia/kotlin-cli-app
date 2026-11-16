# Proposal

## Why

人向けの表示は、登録と最終変更をした人を「管理者 115892231457927168」のように内部 ID で出している。人が読んで誰かを判断するには、表示用の番号（S-・M-）のほうがよい。

## What Changes

- 人向けの表示で、操作した人を表示用の番号で出す（例「管理者 S-000001」「会員本人 M-000001」）
- user・staff の既存の表示と、item・stock の表示（在庫の履歴を含む）の両方に当てる
- 番号が引けないとき（管理者の表が無かった頃の記録など）は内部 ID で出す
- `--json` の `createdBy`・`updatedBy`（`{type, id}`）は変えない

## Capabilities

### New Capabilities

なし

### Modified Capabilities

- `cli-conventions`: 人向けの出力の要件に、操作した人を表示用の番号で出すことを足す

## Impact

- 影響するコード: `apps/cli` の人向けの表示。番号は kotlin-core-app の `ActorNumbers` で引く
- 契約: `--json` は変えない。contractVersion は 1 のまま
