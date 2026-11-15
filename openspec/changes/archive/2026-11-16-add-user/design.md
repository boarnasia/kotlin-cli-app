# Design

## Context

CLI の共通の仕組み（--json の基底・失敗の変換・--db・.env）は手順 1 でできている（openspec/specs の cli-conventions・cli-json-contract）。会員の規則は kotlin-core-app の `lib/core/user` が持つ（その change `add-user` の core-user・core-password）。

## Goals / Non-Goals

**Goals:**

- user のコマンドを、ほかのモジュールのコマンドの型になる形で作る（書き込みの基底・ID の引数・ドメインの失敗の変換）
- 会員の規則を CLI に書かない。値の確かめはユースケースに任せ、CLI は引数の形だけを見る

**Non-Goals:**

- 管理者がいるかどうかの確認（staff モジュールで足す）
- seed からの投入（seed の change で扱う）

## Decisions

### 書き込みのコマンドの基底を足す

`DbCommand` の上に `WriteCommand` を足し、`--actor` を必須の正の整数として持たせる。--actor の形はここで確かめ、管理者がいるかどうかの確認は後で staff モジュールが足す。

### ドメインの失敗は種類だけで終了コードに対応させる

lib/core の失敗の種類を、CLI の ErrorCode に次のように対応させる: NOT_FOUND→NOT_FOUND(3)、CONFLICT→CONFLICT(4)、VALIDATION→VALIDATION(5)、AUTH_FAILED→AUTH_FAILED(6)。対応は入口の 1 か所に置く。

### 会員の ID の引数は形だけを CLI で確かめる

位置引数を core の `EntityRef` で解釈し、どの形でもなければ USAGE にする。種類が違う番号と、該当する会員がいないことは、ユースケースが VALIDATION・NOT_FOUND で返す。

### 操作した人の出し方

core は操作した人を管理者か会員本人の型で記録する（core-user）。CLI からの書き込みは常に管理者（`--actor`）だが、mypage で会員が自分で登録・変更した会員も CLI で読むので、`createdBy`・`updatedBy` は型と ID のオブジェクトで出す。

- 代わりの案: ID の文字列だけを出す → 管理者と会員の区別が付かないので採らない
- 契約の扱い: 会員の data はまだ `docs/json-contract.md` と main spec に載っていないので、この形で初めて載せる。既存の項目の形の変更ではなく項目の追加にあたるので、contractVersion は 1 のまま

### パスワードの入力

`--password-stdin` は標準入力を 1 行読む。付けないときは `System.console()` で表示しない入力を 2 回求める（create・set-password）。authenticate は 1 回だけ求める。console が無ければ USAGE にする。テストでは標準入力を差し替えて `--password-stdin` で渡す。

### app-id

CORE_APP_ID は書き込みで内部 ID を作るときにだけ読む。読むだけのコマンドは CORE_APP_ID が無くても動く。

## Risks / Trade-offs

- [--actor に存在しない管理者の ID を記録できる] → staff モジュールで確認を足すまでの暫定（kotlin-core-app の design.md の「決まったこと」1）
- [表示しない入力は端末に依存し、自動のテストでは確かめにくい] → 自動のテストは --password-stdin で行い、表示しない入力は手で確かめる
