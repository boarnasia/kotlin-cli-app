# Spec Delta

## MODIFIED Requirements

### Requirement: 管理者として操作するコマンドは --actor を受け取る
db のコマンド・user authenticate・staff authenticate・staff bootstrap・seed staff・seed all を除く全てのコマンドは、閲覧を含めて、操作した管理者を --actor で必須で受け取らなければならない（MUST）。--actor は管理者の内部 ID（正の整数）か表示用の番号（S-）を受け付け、どの形でもなければ USAGE（2）で失敗させなければならない（MUST）。その管理者がいない・停止中・要る権限が無いときは、何もせずに FORBIDDEN（7）で失敗させなければならない（MUST）。

#### Scenario: 番号で渡す
- **WHEN** user list に --actor S-000001 を付けて実行する
- **THEN** S-000001 の管理者として実行される

#### Scenario: 停止中の管理者を渡す
- **WHEN** 停止中の管理者を --actor に渡して user get を実行する
- **THEN** 終了コード 7 で失敗し、--json なら error.code は FORBIDDEN になる

#### Scenario: 会員の番号を渡す
- **WHEN** --actor に M-000001 を渡す
- **THEN** 終了コード 2 で失敗する

#### Scenario: 空の DB を作る
- **WHEN** 管理者がまだ 1 人もいない状態で db migrate を実行する
- **THEN** --actor 無しで実行できる

#### Scenario: 空の DB に seed all
- **WHEN** 管理者がまだ 1 人もいない状態で seed all を実行する
- **THEN** --actor 無しで実行でき、最初の管理者が作られる
