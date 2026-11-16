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
