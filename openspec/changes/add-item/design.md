# Design

## Context

staff のコマンドで、`--actor` の基底（`StaffActorCommand`）と、core の失敗の種類を終了コードに対応させる仕組みができている。商品と在庫の規則と権限の確認は core のユースケースが行う（kotlin-core-app の change `add-item`）。

## Goals / Non-Goals

**Goals:**

- item と stock のコマンドを、user・staff と同じ書き方（`StaffActorCommand`・`respond`・人向けの表示）で足す
- 在庫の数は全て JSON の整数で出し、dashboard が計算し直さなくてよいように注文可能数も出す

**Non-Goals:**

- mypage 向けの閲覧（`CatalogService`）のコマンド。mypage-api が使うもので、CLI の利用者は管理者なので作らない
- 引当・戻し・出荷のコマンド。order のコマンドから行う

## Decisions

### 商品の data に在庫を含める

`item list` だけで在庫の少ない商品を見つけられるよう、商品の data に `stock`（onHand・reserved・available）を入れる。`stock show` は在庫だけを、更新の時刻と一緒に返す。

### 数は JSON の整数、ID は文字列

価格と在庫の数は JSON の整数で出す（金額は円単位の整数）。内部 ID は桁あふれを避けるため文字列で出す（cli-json-contract のまま）。数は試作の範囲で 2^53 を超えない。

### `--delta` は負の数を受け付ける

`--delta -3` と `--delta=-3` の両方を受け付ける。Clikt が値の `-3` をオプションと見誤らないかを実装のときに確かめ、だめなら `--delta=-3` の形を README に書く。

### `item delete` に `--yes` を求める

論理削除でも CLI から戻す操作は無いので、`db reset` と同じく `--yes` を求める。

### 商品の指定

商品の引数は core の `EntityRef` で解釈し、UUID や M- の番号は core が VALIDATION にする（staff の引数と同じ）。

## Risks / Trade-offs

- [商品の data に在庫を含めると、商品の形が在庫の変更に引きずられる] → 在庫は `stock` のオブジェクトにまとめ、項目の追加だけで育てる
