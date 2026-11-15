# Spec Delta

## Purpose

dashboard などのプログラムが core-cli の結果を確実に読めるよう、--json の出力の形と終了コードを契約として固定し、互換性を壊さずに育てられるようにする。

## ADDED Requirements

### Requirement: 全てのコマンドが --json を持つ
処理をする全てのコマンドは --json オプションを持ち、付けたときは結果を 1 行の JSON オブジェクトとして標準出力に出さなければならない（MUST）。成功でも失敗でも標準出力に出さなければならない（MUST）。JSON 以外のもの（ログなど）を標準出力に混ぜてはならない（MUST NOT）。

#### Scenario: 成功する
- **WHEN** db status --json を実行して成功する
- **THEN** 標準出力は 1 行の JSON オブジェクトだけになる

#### Scenario: 失敗する
- **WHEN** db status --json を、存在しない core.db を指定して実行する
- **THEN** 失敗を表す JSON オブジェクトが標準出力に出る

### Requirement: 成功と失敗で共通の外側の形を持つ
JSON の最上位は contractVersion（契約の版、今は 1）・ok（成功なら true）・command（実行したコマンド、例 "db status"）を必ず持たなければならない（MUST）。成功なら data にコマンドごとの中身を、失敗なら error に code と message を入れなければならない（MUST）。command が決まる前に失敗したときは、command を null にしなければならない（MUST）。

#### Scenario: 成功の形
- **WHEN** db migrate --json が成功する
- **THEN** {"contractVersion":1,"ok":true,"command":"db migrate","data":{...}} の形で出る

#### Scenario: 失敗の形
- **WHEN** db status --json が core.db が無くて失敗する
- **THEN** {"contractVersion":1,"ok":false,"command":"db status","error":{"code":"DB_NOT_FOUND","message":"..."}} の形で出る

### Requirement: 引数の誤りも --json の形で返す
--json を付けていれば、引数の解析に失敗したとき（必須のオプションが無い、知らないコマンドなど）も、失敗の JSON を返さなければならない（MUST）。error.code は USAGE とし、message には何が誤っているかを入れなければならない（MUST）。

#### Scenario: 必須のオプションが無い
- **WHEN** db status --json を --db も CORE_DB_PATH も無しで実行する
- **THEN** 終了コード 2 で、error.code が USAGE、message に --db が含まれる JSON が出る

#### Scenario: 知らないコマンド
- **WHEN** db nope --json を実行する
- **THEN** 終了コード 2 で、error.code が USAGE の JSON が出る

### Requirement: 値の表し方を揃える
値の無い項目は省かずに null で出さなければならない（MUST）。時刻は UTC の ISO-8601 文字列（ミリ秒 3 桁、末尾 Z）で出さなければならない（MUST）。内部 ID は桁あふれを避けるため文字列で出し、行を出すときは内部 ID・表示用の番号・公開 ID（持つものだけ）を全て出さなければならない（MUST）。金額は円単位の整数で出さなければならない（MUST）。

#### Scenario: 未適用のマイグレーション
- **WHEN** 未適用のマイグレーションがある core.db で db status --json を実行する
- **THEN** そのマイグレーションの installedAt は省かれずに null で出る

#### Scenario: 時刻を出す
- **WHEN** db migrate --json でマイグレーションを適用する
- **THEN** installedAt は 2026-11-16T03:00:00.000Z のような UTC の文字列で出る

### Requirement: 失敗の種類ごとに終了コードを決める
終了コードは、成功 0・想定外のエラー INTERNAL 1・引数の誤り USAGE 2・対象が無い NOT_FOUND 3・core.db が無い DB_NOT_FOUND 3・状態の不一致や重複や在庫不足 CONFLICT 4・入力の値の誤り VALIDATION 5・パスワードの照合の失敗 AUTH_FAILED 6 でなければならない（MUST）。error.code は終了コードと同じ種類を表さなければならない（MUST）。

#### Scenario: core.db が無い
- **WHEN** 存在しない core.db を指定して db status を実行する
- **THEN** 終了コードは 3 で、--json なら error.code は DB_NOT_FOUND になる

#### Scenario: 想定外のエラー
- **WHEN** 実行中に想定外の例外が起きる
- **THEN** 終了コードは 1 で、--json なら error.code は INTERNAL になる

### Requirement: 契約は互換性を保って変える
dashboard は error.code と data の項目で判定するので、項目を足すことは許すが、項目の名前を変える・消す・型を変える・error.code の文字列や終了コードを変えるときは、contractVersion を上げなければならない（MUST）。error.message の文言は人向けで、変わってもよい。契約の内容は docs/json-contract.md に書き、変えたら同時に直さなければならない（MUST）。

#### Scenario: 項目を足す
- **WHEN** あるコマンドの data に新しい項目を足す
- **THEN** contractVersion は 1 のままで、docs/json-contract.md にその項目を書き足す

#### Scenario: 項目の名前を変える
- **WHEN** data の既存の項目の名前を変える
- **THEN** contractVersion を 2 に上げる
