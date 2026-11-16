# Spec Delta

## MODIFIED Requirements

### Requirement: 失敗の種類ごとに終了コードを決める
終了コードは、成功 0・想定外のエラー INTERNAL 1・引数の誤り USAGE 2・対象が無い NOT_FOUND 3・core.db が無い DB_NOT_FOUND 3・状態の不一致や重複や在庫不足 CONFLICT 4・入力の値の誤り VALIDATION 5・パスワードの照合の失敗 AUTH_FAILED 6・操作した管理者がいない／停止中／権限が無い FORBIDDEN 7 でなければならない（MUST）。error.code は終了コードと同じ種類を表さなければならない（MUST）。

#### Scenario: core.db が無い
- **WHEN** 存在しない core.db を指定して db status を実行する
- **THEN** 終了コードは 3 で、--json なら error.code は DB_NOT_FOUND になる

#### Scenario: 想定外のエラー
- **WHEN** 実行中に想定外の例外が起きる
- **THEN** 終了コードは 1 で、--json なら error.code は INTERNAL になる

#### Scenario: 権限の無い管理者
- **WHEN** 権限の無い管理者を --actor に渡して書き込みのコマンドを実行する
- **THEN** 終了コードは 7 で、--json なら error.code は FORBIDDEN になる

### Requirement: 契約は互換性を保って変える
dashboard は error.code と data の項目で判定するので、項目を足すことは許すが、項目の名前を変える・消す・型を変える・error.code の文字列や終了コードを変えるときは、contractVersion を上げなければならない（MUST）。error.code の種類と終了コードは、contractVersion を上げずに増えることがある。呼び出し側は、知らない error.code や終了コードを失敗として扱わなければならない（MUST）。error.message の文言は人向けで、変わってもよい。契約の内容は docs/json-contract.md に書き、変えたら同時に直さなければならない（MUST）。

#### Scenario: 項目を足す
- **WHEN** あるコマンドの data に新しい項目を足す
- **THEN** contractVersion は 1 のままで、docs/json-contract.md にその項目を書き足す

#### Scenario: 項目の名前を変える
- **WHEN** data の既存の項目の名前を変える
- **THEN** contractVersion を 2 に上げる

#### Scenario: 失敗の種類を足す
- **WHEN** 新しい error.code と終了コード（例 FORBIDDEN の 7）を足す
- **THEN** contractVersion は 1 のままで、docs/json-contract.md に書き足し、知らないコードを受けた呼び出し側はそれを失敗として扱う
