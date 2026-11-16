# Spec Delta

## Purpose

管理者が CLI から会員の代わりに注文し、注文を確認し、キャンセルの申請を承認・却下し、注文を完了でき、dashboard がその結果を --json で読めるようにする。

## ADDED Requirements

### Requirement: 注文の data の形
注文を返すコマンドの --json は、注文を次の項目を持つオブジェクトで出さなければならない（MUST）。id（内部 ID の文字列）・displayNumber（例 "O-000001"）・publicId（UUID の文字列）・member（id と displayNumber を持つオブジェクト）・status（"accepted"・"cancel_requested"・"cancelled"・"completed" のどれか）・total（合計金額、円の整数）・lines（明細の配列）・placedAt・completedAt・cancelledAt・events（履歴の配列）・createdAt・updatedAt・createdBy・updatedBy。

#### Scenario: 注文した直後
- **WHEN** order place --json で注文する
- **THEN** status は "accepted"、completedAt と cancelledAt は null、events は 1 件になる

### Requirement: 明細と履歴の形
明細は lineNo・itemId・itemDisplayNumber・name（注文した時点の商品名）・unitPrice（注文した時点の単価）・quantity・subtotal を持たなければならない（MUST）。履歴は kind（"placed"・"cancel_requested"・"cancel_rejected"・"cancel_approved"・"completed"）・fromStatus（作成では null）・toStatus・reason（無ければ null）・createdAt・createdBy を持ち、古い順に並ばなければならない（MUST）。

#### Scenario: 作成の履歴
- **WHEN** order place --json で単価 300 円の商品を 2 個注文する
- **THEN** 明細の unitPrice は 300、subtotal は 600 で、events の 1 件目は kind "placed"・fromStatus null・toStatus "accepted" になる

### Requirement: 注文の指定
注文を指定する引数は、内部 ID・表示用の番号（O-）・公開 ID（UUID）を受け付けなければならない（MUST）。どの ID の形でもなければ USAGE（2）、ほかの種類の番号は VALIDATION（5）、いなければ NOT_FOUND（3）とする。

#### Scenario: 公開 ID で取り出す
- **WHEN** 注文の公開 ID で order get を実行する
- **THEN** 終了コード 0 で、その注文が data に出る

### Requirement: order place は会員の代わりに注文する
order place は --user（会員の内部 ID・M- の番号・公開 ID）・--item（"<商品>:<数>" の形。商品は内部 ID か I- の番号。1 回以上繰り返す）・--actor を受け取り、注文を作って data に出さなければならない（MUST）。--item が無い・形が違う・数が整数でないときは USAGE（2）、数の範囲の外・明細が 21 件以上・同じ商品の重複は VALIDATION（5）とする。在庫不足・注文できない商品・停止中の会員は CONFLICT（4）、いない会員は NOT_FOUND（3）、order:write の無い管理者は FORBIDDEN（7）とする。内部 ID を作るため、CORE_APP_ID が要る。

#### Scenario: 2 つの商品を注文する
- **WHEN** order place --user M-000001 --item I-000001:2 --item I-000002:1 --actor S-000001 --json を実行する
- **THEN** 終了コード 0 で、明細 2 行と合計金額を持つ O-000001 の注文が data に出る

#### Scenario: 在庫不足
- **WHEN** 注文可能数 1 の商品に --item I-000001:2 で order place を実行する
- **THEN** 終了コード 4 で失敗し、注文は作られず、引当は変わらない

#### Scenario: --item の形が違う
- **WHEN** --item I-000001 で order place を実行する
- **THEN** 終了コード 2 で失敗する

### Requirement: order list と order get は注文を返す
order list は --status・--user・--limit・--offset を受け取り、data に {items, total, limit, offset}（items は受け付けた時刻の新しい順）を出さなければならない（MUST）。order get は注文 1 つを data に出さなければならない（MUST）。どちらも order:read（order:write・order:cancel-approve が含む）が要り、無ければ FORBIDDEN（7）とする。

#### Scenario: 会員で絞り込む
- **WHEN** order list --user M-000001 --actor S-000001 --json を実行する
- **THEN** M-000001 の注文だけが新しい順に出る

### Requirement: キャンセルと完了のコマンド
order request-cancel は --reason（省ける）を受け取り、受付済みの注文をキャンセル申請中にしなければならない（MUST）。order approve-cancel はキャンセル申請中の注文をキャンセル済みにして引当を戻し、order reject-cancel は --reason（必須）を受け取って受付済みに戻し、order complete は受付済みの注文を完了にして出荷しなければならない（MUST）。どれも変えた注文を data に出す。許されない遷移は CONFLICT（4）、理由の誤りは VALIDATION（5）とする。request-cancel と complete は order:write、approve-cancel と reject-cancel は order:cancel-approve が要る。

#### Scenario: 申請して承認する
- **WHEN** order request-cancel O-000001 --reason 間違えた の後に order approve-cancel O-000001 を実行する
- **THEN** status は "cancelled" になり、events は placed・cancel_requested・cancel_approved の 3 件になり、商品の引当が戻る

#### Scenario: 理由を付けずに却下する
- **WHEN** order reject-cancel O-000001 --actor S-000001 を --reason 無しで実行する
- **THEN** 終了コード 2 で失敗する

#### Scenario: 申請中の注文を完了する
- **WHEN** キャンセル申請中の注文に order complete を実行する
- **THEN** 終了コード 4 で失敗する

### Requirement: 注文の人向けの表示
人向けの order list は 1 つの注文を 1 行で、番号・状態・合計金額・会員の番号・受け付けた時刻（JST）・明細の要約を出し、最後に全件数を出さなければならない（MUST）。order get などは属性を 1 項目 1 行で、明細を 1 行ずつ、履歴を 1 件 1 行で、操作した人を表示用の番号で出さなければならない（MUST）。

#### Scenario: 注文を見る
- **WHEN** order get O-000001 --actor S-000001 を --json 無しで実行する
- **THEN** 明細ごとに商品の番号・名前・単価・数・小計が出て、履歴に操作した人の番号が出る
