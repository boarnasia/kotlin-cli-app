# Design

## Context

user のコマンドで、書き込みの基底（`WriteCommand`、`--actor` は内部 ID だけ）と、core の失敗の種類を終了コードに対応させる仕組みができている（openspec/specs の cli-user-commands）。権限の確認は core のユースケースが行う（kotlin-core-app の change `add-staff` の core-authorization）。

## Goals / Non-Goals

**Goals:**

- `--actor` の受け付け方を 1 か所にまとめ、user・staff と、この後のモジュールのコマンドで同じにする
- CLI では権限を確かめない。確かめるのは core のユースケース

**Non-Goals:**

- dashboard 用の管理者の作成（運用で `staff create` を使う）

## Decisions

### `--actor` の基底を「管理者として操作するコマンド」にする

`WriteCommand` を `StaffActorCommand` に置き換え、閲覧のコマンドもこの基底を使う。`--actor` は core の `EntityRef` で解釈し、内部 ID と S- の番号だけを受け付ける（会員の番号と UUID は USAGE）。S- の番号は core の `StaffService` で内部 ID に引き直す。いない番号はここで FORBIDDEN にする。引き直した ID がいて有効で権限を持つかは、ユースケースがトランザクションの中で確かめ直す。

- 代わりの案: core の各ユースケースに `EntityRef` のまま渡す → `Actor` の型が番号を持つことになり、記録の形と食い違うので採らない

### 権限の名前は core の型からそのまま出す

`--permission`・`--grant`・`--revoke` は core の権限の型の名前（`member:read` など）の選択肢にし、知らない名前は Clikt が USAGE にする。JSON の `permissions` も同じ名前を core の決めた順に出す。

### FORBIDDEN を終了コード 7 にする

`ErrorCode` に `FORBIDDEN(7)` を足し、core の `CoreErrorKind.FORBIDDEN` を対応させる。既存のコードと終了コードは変えないので、contractVersion は 1 のまま（項目の追加）。あわせて、error.code と終了コードは今後増えることがあり、呼び出し側は知らないコードを失敗として扱う、と cli-json-contract と `docs/json-contract.md` に書く。

### `staff bootstrap`

`--actor` を取らない唯一の書き込みのコマンド。`DbCommand` を直接継ぎ、CORE_APP_ID を読んで内部 ID を作る。管理者がいるかどうかは core が書き込みのトランザクションの中で確かめる。

## Risks / Trade-offs

- [閲覧にも `--actor` が要るので、dashboard の呼び方が変わる（BREAKING）] → dashboard 用に読む権限だけを持つ管理者を作り、その番号を渡す。kotlin-core-app の design.md の Open Questions 2 で決まった
- [S- の番号を引き直してからユースケースが確かめるまでに、その管理者が停止されうる] → ユースケースがトランザクションの中で確かめ直すので、停止された管理者の操作は通らない
