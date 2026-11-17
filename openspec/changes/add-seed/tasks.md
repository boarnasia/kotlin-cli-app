# Tasks

2026-11-17 に上原の承認を得た（判断が要る点は design.md のとおりに決まった）。

## 1. core の読み取りの関数（kotlin-core-app）

- [x] 1.1 `lib/core/db` の `DisplayNumbers` に、種類ごとに最後に払い出した値を読む関数を足し、登録・取り消し・論理削除のあとの値をテストで確かめる（`./gradlew test` が通る）

## 2. 定義ファイルの読み込みと確認

- [x] 2.1 5 つの定義ファイルの型と読み込み（同梱・`--dir`）を作り、ファイルが無い・JSON の誤り・知らない項目・要る項目が無いときの VALIDATION（5）をテストで確かめる
- [x] 2.2 書く前の参照の確認（定義にも DB にも無い会員・商品、2 つに当たる商品名）を作り、何も書かれずに 5 になることをテストで確かめる

## 3. モジュールごとの seed

- [x] 3.1 `seed staff`: bootstrap と残りの登録・停止、管理者がいるときの CONFLICT（4）をテストで確かめる
- [x] 3.2 `seed user`: 登録と停止、`--actor` の権限不足の FORBIDDEN（7）、2 回目の 4 をテストで確かめる
- [x] 3.3 `seed item`: 登録・初期在庫の調整（理由「初期在庫」）・公開・削除をテストで確かめる
- [x] 3.4 `seed order`: 注文と操作の並び（キャンセル済み・完了・却下を経た状態）、引当と在庫の履歴、DB の会員・商品の参照をテストで確かめる
- [x] 3.5 `seed notice`: 相対の日時・今すぐ終了・論理削除で、status が下書き・予約・公開中・終了・削除済みになることをテストで確かめる
- [x] 3.6 途中で core が失敗したとき（在庫不足・遷移に合わない操作・過去の公開終了・値の誤り）に、core の終了コードで止まり、位置・書いた分が残ること・`db reset --yes` の案内がエラーに出ることをテストで確かめる

## 4. seed all と出力

- [x] 4.1 `seed all`: 空の DB への投入・どれかのモジュールにデータがあるときに何も書かずに 4・操作した人が最初の管理者であることをテストで確かめる
- [x] 4.2 `--json` の data（modules・created）と人向けの 1 行の表示をテストで確かめる
- [x] 4.3 既定の定義ファイル（design.md の「既定の定義の中身」、パスワードは `demo-pass-001` の形）を書き、`seed all` が通ることをテストで確かめる
- [x] 4.4 `docs/json-contract.md` に seed の data と `--actor` を取らないコマンドを、`README.md` に使い方・定義ファイルの形・パスワードは架空のものだけを書くことを足す

## 5. 動作の確認

- [x] 5.1 `installDist` した core-cli を別のディレクトリに複製し、`db reset --yes` → `seed all` → 各 list・`stock history`・`user authenticate`・`staff authenticate` を実行し、2 回目の `seed all`・データのある `seed item` が 4、形と参照の誤りが 5、`--dir`、`--actor` 無しの 2・権限不足の 7 を確かめる

## Workflow follow-up

- core の commit（1.1）と CLI の commit を分ける
- 実装と確認のあと、change を archive して `openspec validate --all --strict` を通す
