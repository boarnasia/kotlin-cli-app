# Design

## Context

item と stock のコマンドで、`StaffActorCommand`・`respond`・操作した人の番号での表示（`ActorLabels`）ができている。注文の規則と権限の確認は core のユースケースが行う（kotlin-core-app の change `add-order`）。CLI の利用者は管理者なので、管理者向けの `OrderService` だけを使う。

## Goals / Non-Goals

**Goals:**

- order のコマンドを、item・stock と同じ書き方で足す
- 注文の data に明細と履歴を含め、dashboard が 1 回の取得で注文を表示できるようにする

**Non-Goals:**

- mypage 向けの `MemberOrderService` のコマンド。mypage-api が使うもので、会員として CLI を使う場面が無いため作らない

## Decisions

### --item は "<商品>:<数>" の形で繰り返す

1 つの注文に複数の商品を持たせるため、`--item I-000001:2 --item I-000002:1` のように商品と数を組で受け取る。商品と数を別のオプション（`--item` と `--quantity`）にすると、並びの対応を取り違えうるため。

### 注文の data に明細と履歴を含める

一覧でも明細と履歴を含め、取得と同じ形にする。試作の量（件数の上限 500）なら大きくならない。会員は `member: {id, displayNumber}` で出す（注文を開いた人が会員を引き直さなくてよいように）。明細は `itemDisplayNumber` も出す。

### 状態の値

`accepted`・`cancel_requested`・`cancelled`・`completed`。人向けは「受付済み」「キャンセル申請中」「キャンセル済み」「完了」。

### reject-cancel の --reason は必須のオプション

付け忘れは引数の誤り（USAGE）として、core を呼ぶ前に止める。空の文字列は core が VALIDATION にする。

## Risks / Trade-offs

- [一覧に履歴まで含めると、注文が多いと出力が大きくなる] → 件数は最大 500。必要になったら、項目を減らした一覧の形を項目の追加で足す
