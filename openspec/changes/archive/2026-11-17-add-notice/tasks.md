# Tasks

kotlin-core-app の change `add-notice` の実装が終わってから始める。

## 1. コマンド

- [x] 1.1 お知らせの data の形と、日時の入力（時差あり・無しの JST・形の誤りの 2）をテストで確かめる
- [x] 1.2 notice create・get・list・update（消去のオプション・何も無いときの 5）をテストで確かめる
- [x] 1.3 notice end・delete（--yes）・visible（--at）と、許されない操作の 4・権限の 7 をテストで確かめる
- [x] 1.4 人向けの表示（1 行の中身・JST の期間・操作した人の番号）をテストで確かめる

## 2. 文書と動作の確認

- [x] 2.1 お知らせの data の形を `docs/json-contract.md` に、コマンドを `README.md` に書く
- [x] 2.2 `installDist` した core-cli で、下書き → 予約 → visible --at → 公開中の update → end → delete を順に実行して確かめる

## Workflow follow-up

- kotlin-core-app の change `add-notice` と合わせて archive する
