# Spec Delta

## Purpose

core.db のスキーマを作る・確かめる・作り直す操作を CLI から行えるようにし、マイグレーションの適用をこの CLI の 1 か所にまとめる。

## ADDED Requirements

### Requirement: db migrate は未適用のマイグレーションを適用する
db migrate は未適用のマイグレーションを全て適用しなければならない（MUST）。core.db のファイルが無ければ作らなければならない（MUST）。ファイルを置くディレクトリが無ければ、作らずに DB_NOT_FOUND で失敗させなければならない（MUST）。--json の data は path（core.db の絶対パス）・appliedCount・applied（適用したマイグレーション）・currentVersion でなければならない（MUST）。

#### Scenario: 新しい DB を作る
- **WHEN** 存在しないファイルを指定して db migrate --json を実行する
- **THEN** core.db が作られ、appliedCount にマイグレーションの件数、currentVersion に最新の版が入る

#### Scenario: 適用済みの DB
- **WHEN** 全て適用済みの core.db に db migrate を実行する
- **THEN** 終了コード 0 で、appliedCount は 0、applied は空になる

#### Scenario: ディレクトリが無い
- **WHEN** 存在しないディレクトリの中のパスを指定して db migrate を実行する
- **THEN** 終了コード 3 で失敗し、ディレクトリもファイルも作られない

### Requirement: db status は core.db を作らずに適用状況を返す
db status は、core.db のファイルが無ければ作らずに DB_NOT_FOUND で失敗させなければならない（MUST）。--json の data は path・currentVersion・pendingCount・migrations でなければならない（MUST）。migrations の各要素は version・description・state・installedAt を持ち、state は applied・pending・failed・other のどれかでなければならない（MUST）。

#### Scenario: 適用後の状況
- **WHEN** db migrate の後に db status --json を実行する
- **THEN** pendingCount は 0 で、各マイグレーションの state は applied になる

#### Scenario: core.db が無い
- **WHEN** 存在しないファイルを指定して db status を実行する
- **THEN** 終了コード 3 で失敗し、そのパスにファイルは作られない

### Requirement: db reset は確認を求めてから作り直す
db reset は全てのデータを消すので、--yes が無ければ何もせずに USAGE で失敗させなければならない（MUST）。--yes があれば、core.db の中身を全て消してマイグレーションを最初から適用し直さなければならない（MUST）。core.db のファイルが無ければ DB_NOT_FOUND で失敗させなければならない（MUST）。--json の data は db migrate と同じ形でなければならない（MUST）。

#### Scenario: --yes が無い
- **WHEN** db reset を --yes 無しで実行する
- **THEN** 終了コード 2 で失敗し、core.db の中身は変わらない

#### Scenario: --yes がある
- **WHEN** db reset --yes --json を実行する
- **THEN** 中身が消えてマイグレーションが適用し直され、appliedCount にマイグレーションの件数が入る

### Requirement: マイグレーションの適用は db migrate と db reset だけが行う
core.db へのマイグレーションの適用は、db migrate と db reset だけが行わなければならない（MUST）。ほかのコマンドは、未適用のマイグレーションがあっても適用してはならない（MUST NOT）。

#### Scenario: 未適用の DB で状況を見る
- **WHEN** 未適用のマイグレーションがある core.db に db status を実行する
- **THEN** マイグレーションは適用されず、未適用の件数が返る
