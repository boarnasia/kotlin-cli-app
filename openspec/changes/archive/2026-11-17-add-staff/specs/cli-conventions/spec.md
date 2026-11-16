# Spec Delta

## ADDED Requirements

### Requirement: 管理者として操作するコマンドは --actor を受け取る
db のコマンド・user authenticate・staff authenticate・staff bootstrap を除く全てのコマンドは、閲覧を含めて、操作した管理者を --actor で必須で受け取らなければならない（MUST）。--actor は管理者の内部 ID（正の整数）か表示用の番号（S-）を受け付け、どの形でもなければ USAGE（2）で失敗させなければならない（MUST）。その管理者がいない・停止中・要る権限が無いときは、何もせずに FORBIDDEN（7）で失敗させなければならない（MUST）。

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

## REMOVED Requirements

### Requirement: 書き込みのコマンドは操作した管理者を受け取る
**Reason**: 閲覧のコマンドにも --actor が要るようになり、受け付ける形（S- の番号）と確認（FORBIDDEN）が加わったため、「管理者として操作するコマンドは --actor を受け取る」に置き換える
**Migration**: 閲覧のコマンドにも --actor を渡す。書き込みのコマンドの --actor はそのまま使える
