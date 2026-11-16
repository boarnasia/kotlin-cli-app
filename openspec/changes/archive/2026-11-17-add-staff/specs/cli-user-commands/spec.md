# Spec Delta

## ADDED Requirements

### Requirement: user のコマンドは --actor で操作した管理者を受け取る
user create・list・get・update・suspend・activate・set-password は、--actor に管理者の内部 ID か S- の番号を必須で取らなければならない（MUST）。無いときやどの形でもないときは USAGE（2）、いない・停止中・権限が無い管理者は FORBIDDEN（7）で、何もせずに失敗させなければならない（MUST）。要る権限は、list・get が member:read、それ以外が member:write とする（core-authorization）。user authenticate は --actor を取ってはならない（MUST NOT）。

#### Scenario: --actor が無い
- **WHEN** user create を --actor 無しで実行する
- **THEN** 終了コード 2 で失敗し、会員は作られない

#### Scenario: 閲覧の権限だけで停止する
- **WHEN** member:read だけを持つ管理者を --actor に渡して user suspend を実行する
- **THEN** 終了コード 7 で失敗し、会員は変わらない

#### Scenario: いない管理者
- **WHEN** どの管理者にも当たらない S-999999 を --actor に渡して user get を実行する
- **THEN** 終了コード 7 で、error.code は FORBIDDEN になる

## REMOVED Requirements

### Requirement: 書き込みのコマンドは --actor に内部 ID を取る
**Reason**: --actor が閲覧にも必須になり、S- の番号と管理者の確認が加わったため、「user のコマンドは --actor で操作した管理者を受け取る」に置き換える
**Migration**: user list・user get にも --actor を渡す。内部 ID の --actor はそのまま使える
