# Tasks

kotlin-core-app の change `add-item` の実装（lib/core/item）が終わってから始める。kotlin-core-app の design.md の Open Questions は推奨案で書いている。

## 1. item のコマンド

- [ ] 1.1 商品の data の形を作り、13 項目・stock の 3 つの数・公開 ID が出ないことをテストで確かめる
- [ ] 1.2 item create・list・get を足し、I- の番号・非公開で始まること・削除済みを除く一覧・--status deleted・価格が整数でないときの終了コード 2・商品以外の番号の終了コード 5・権限の無い管理者の閲覧と、item:write の無い登録の終了コード 7 をテストで確かめる
- [ ] 1.3 item update を足し、属性の変更・--description "" で消すこと・何も指定しないときの終了コード 5 をテストで確かめる
- [ ] 1.4 item publish・unpublish・delete を足し、状態と時刻・許されない遷移の終了コード 4・--yes が無いときの終了コード 2・引当のある商品の削除の終了コード 4 をテストで確かめる（引当は core の `StockLedger` でテストの中で作る）

## 2. stock のコマンド

- [ ] 2.1 在庫と変動の data の形を作り、available が onHand − reserved であることをテストで確かめる
- [ ] 2.2 stock show・adjust・history を足し、負の --delta・--delta 0 の終了コード 5・引当を下回る調整の終了コード 4・履歴の新しい順と件数・item:write の無い調整の終了コード 7 をテストで確かめる

## 3. 人向けの表示

- [ ] 3.1 item list・item get・stock show・stock history の人向けの表示を足し、1 行の中身と JST の時刻をテストで確かめる

## 4. 文書と動作の確認

- [ ] 4.1 商品・在庫・変動の data の形を `docs/json-contract.md` に、コマンドを `README.md` に書く
- [ ] 4.2 `installDist` した core-cli で、db reset → staff bootstrap → item create → stock adjust → item publish → item list → 権限の無い調整（終了コード 7）→ 引当を下回る調整（終了コード 4）→ item delete --yes → stock history を順に実行し、出力と終了コードが仕様どおりであることを確かめる

## Workflow follow-up

- kotlin-core-app の change `add-item` と合わせて archive し、`openspec/specs/` に cli-item-commands・cli-stock-commands が反映されたことを確かめる
