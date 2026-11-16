# Spec Delta

## MODIFIED Requirements

### Requirement: 人向けの出力は日本時間で表示し、エラーは標準エラーに出す
--json を付けないときは、人が読む形で標準出力に出さなければならない（MUST）。時刻は日本時間（JST）で表示しなければならない（MUST）。操作した人（登録・最終変更・在庫の変動をした人）は、表示用の番号で「管理者 S-000001」「会員本人 M-000001」の形で出さなければならない（MUST）。番号が引けない人は内部 ID で出す。--json の createdBy・updatedBy は内部 ID のまま変えてはならない（MUST NOT）。失敗したときは、理由を標準エラーに出し、標準出力には何も出してはならない（MUST NOT）。

#### Scenario: 適用状況を人向けに表示する
- **WHEN** --json を付けずに db status を実行する
- **THEN** マイグレーションを適用した時刻が JST で表示される

#### Scenario: 人向けの表示で失敗する
- **WHEN** --json を付けずに、存在しない core.db を指定して db status を実行する
- **THEN** 標準出力は空で、標準エラーに core.db が無いことが出る

#### Scenario: 操作した人を番号で出す
- **WHEN** S-000002 が名前を変えた会員を、--json を付けずに user get で表示する
- **THEN** 最終変更の行に「管理者 S-000002」が出て、--json の updatedBy は {"type":"staff","id":"<S-000002 の内部 ID>"} のまま

#### Scenario: 番号が引けない
- **WHEN** 登録した人の内部 ID がどの管理者にも当たらない会員を表示する
- **THEN** 登録の行に「管理者 <その内部 ID>」が出る
