# Proposal

## Why

dashboard や mypage-api・admin-api の試作を始めるには、管理者・会員・商品・注文・お知らせがひととおり揃った core.db が要る。今は README の手順のようにコマンドを 1 つずつ打つしかなく、注文の状態（キャンセル済み・完了など）やお知らせの状態（予約・終了など）を揃えるのに手間がかかる。定義ファイルから決まった内容を投入できるようにする。

## What Changes

- `seed staff|user|item|order|notice|all` を足す。定義ファイル（JSON）から投入する
- 投入の順番は staff → user → item → order → notice（kotlin-core-app の add-staff の design.md の「seed の順番」）。`seed all` はこの順に全てを投入する
- 最初の管理者は `staff bootstrap` と同じ方法（core の bootstrap）で作る。`seed staff` と `seed all` は `--actor` を取らず、作った最初の管理者が残りを登録する
- `seed user|item|order|notice` は他のコマンドと同じく `--actor` を必須で取り、権限の確認は core のユースケースに任せる
- 既定の定義ファイルを CLI に同梱し、`--dir` で別のディレクトリの定義ファイルを使えるようにする
- 投入先のモジュールにデータが 1 件でもあれば、何も書かずに CONFLICT（4）で止める。やり直すときは `db reset --yes` で作り直す
- 書く前に定義ファイル全体（形・参照・在庫の数・状態の組み合わせ）を確かめ、誤りは何も書かずに VALIDATION（5）で止める
- 注文は、作ったあとに定義した操作（キャンセルの申請・承認・却下、完了）を順に行い、完了・キャンセル済みなどの状態も作る
- seed の --json の形（data）を `docs/json-contract.md` に足す

## Capabilities

### New Capabilities

- `cli-seed-commands`: seed の各コマンドの入力（定義ファイルの形と置き場所）・投入の順番・既にあるデータとの関係・失敗と終了コード・出力

### Modified Capabilities

- `cli-conventions`: `--actor` を取らないコマンドに `seed staff` と `seed all` を加える

## Impact

- 影響するコード: `apps/cli` に seed のコマンドと既定の定義ファイル（リソース）を追加。投入は既存のユースケースだけで行う。kotlin-core-app は、モジュールにデータがあるかを読む関数を `lib/core/db` に 1 つ足すだけで、仕様は変えない
- 依存: 増やさない（定義ファイルの読み込みは既に使っている kotlinx.serialization の JSON）
- 契約: data の形の追加だけで、contractVersion は 1 のまま
- 文書: `docs/json-contract.md`、`README.md`
