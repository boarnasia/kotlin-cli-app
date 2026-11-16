# Tasks

kotlin-core-app の change `add-order` の実装（lib/core/order）が終わってから始める。kotlin-core-app の design.md の Open Questions は推奨案で書いている。

## 1. order のコマンド

- [ ] 1.1 注文の data の形を作り、15 項目・明細の 7 項目・履歴の 6 項目・member の形をテストで確かめる
- [ ] 1.2 order place を足し、明細と合計・--item の形の誤りの終了コード 2・数の範囲の外と重複の 5・在庫不足と注文できない商品と停止中の会員の 4・いない会員の 3・order:write の無い管理者の 7・CORE_APP_ID が無いときの 2 をテストで確かめる
- [ ] 1.3 order list・get を足し、3 種類の ID・他の種類の番号の 5・--status と --user の絞り込み・新しい順・order:read の無い管理者の 7 をテストで確かめる
- [ ] 1.4 order request-cancel・approve-cancel・reject-cancel・complete を足し、状態と履歴・引当の戻しと出荷・許されない遷移の 4・reject-cancel の --reason が無いときの 2・権限の割り当てをテストで確かめる

## 2. staff の権限

- [ ] 2.1 staff bootstrap の permissions が 8 種類を決めた順に並ぶこと、--permission order:write を受け付けることをテストで確かめる

## 3. 人向けの表示

- [ ] 3.1 order list と order get の人向けの表示を足し、1 行の中身・明細・履歴・JST の時刻・操作した人の番号をテストで確かめる

## 4. 文書と動作の確認

- [ ] 4.1 注文の data の形を `docs/json-contract.md` に、コマンドと 8 種類の権限を `README.md` に書く
- [ ] 4.2 `installDist` した core-cli で、db reset → staff bootstrap → user create → item create・stock adjust・item publish → order place → 在庫不足の order place（4）→ order request-cancel → order reject-cancel → order complete → 2 件目の order place → request-cancel → approve-cancel → stock history → order list を順に実行し、出力と終了コードと在庫の数が仕様どおりであることを確かめる

## Workflow follow-up

- kotlin-core-app の change `add-order` と合わせて archive し、`openspec/specs/` に cli-order-commands と cli-staff-commands の修正が反映されたことを確かめる
