# cli-conventions Specification

## Purpose
core-cli の全てのコマンドが、同じやり方で DB と設定を受け取り、誰が操作したかを残し、人にもプログラムにも読める結果を返すようにする。

## Requirements

### Requirement: DB は --db か環境変数で指定する
core.db を使うコマンドは、DB のパスを --db オプションか環境変数 CORE_DB_PATH で受け取らなければならない（MUST）。両方あれば --db を使わなければならない（MUST）。どちらも無ければ、引数の誤り（USAGE）として失敗させなければならない（MUST）。

#### Scenario: 環境変数だけで指定する
- **WHEN** CORE_DB_PATH を設定し、--db を付けずに db migrate を実行する
- **THEN** CORE_DB_PATH のパスの core.db が使われる

#### Scenario: どちらも無い
- **WHEN** --db も CORE_DB_PATH も無しに db status を実行する
- **THEN** 終了コード 2 で失敗し、--db が無いことが示される

### Requirement: カレントディレクトリの .env を読む
CLI は起動時にカレントディレクトリの .env を読み、環境変数と同じように使わなければならない（MUST）。同じ名前の環境変数があれば、環境変数を優先しなければならない（MUST）。.env が無くても動かなければならない（MUST）。空行と # で始まる行は無視し、値を囲む引用符は外さなければならない（MUST）。

#### Scenario: .env と環境変数の両方にある
- **WHEN** .env に CORE_DB_PATH=a.db があり、環境変数で CORE_DB_PATH=b.db を指定する
- **THEN** b.db が使われる

#### Scenario: .env だけにある
- **WHEN** .env に CORE_DB_PATH があり、環境変数にも --db にも無い
- **THEN** .env のパスの core.db が使われる

### Requirement: CLI の app-id は 3 にする
CLI が内部 ID を作るときは、.env か環境変数の CORE_APP_ID で受け取った app-id を lib/core に渡さなければならない（MUST）。CLI に割り当てた app-id は 3 で、割り当ては kotlin-core-app の docs/ids.md に従わなければならない（MUST）。

#### Scenario: CLI が登録する
- **WHEN** CORE_APP_ID=3 の設定で、内部 ID を作る登録のコマンドを実行する
- **THEN** 作られた内部 ID の app-id は 3 になる

### Requirement: 書き込みのコマンドは操作した管理者を受け取る
core.db のデータを書き換えるコマンドは、操作した管理者の ID を --actor で受け取らなければならない（MUST）。--actor が無ければ、書き込まずに失敗させなければならない（MUST）。db のコマンド（migrate・status・reset）は、管理者の表ができる前にも実行するので、--actor を取ってはならない（MUST NOT）。

#### Scenario: --actor の無い書き込み
- **WHEN** --actor を付けずに、データを書き換えるコマンドを実行する
- **THEN** 何も書き込まれず、引数の誤りとして失敗する

#### Scenario: 空の DB を作る
- **WHEN** 管理者がまだ 1 人もいない状態で db migrate を実行する
- **THEN** --actor 無しで実行できる

### Requirement: 人向けの出力は日本時間で表示し、エラーは標準エラーに出す
--json を付けないときは、人が読む形で標準出力に出さなければならない（MUST）。時刻は日本時間（JST）で表示しなければならない（MUST）。失敗したときは、理由を標準エラーに出し、標準出力には何も出してはならない（MUST NOT）。

#### Scenario: 適用状況を人向けに表示する
- **WHEN** --json を付けずに db status を実行する
- **THEN** マイグレーションを適用した時刻が JST で表示される

#### Scenario: 人向けの表示で失敗する
- **WHEN** --json を付けずに、存在しない core.db を指定して db status を実行する
- **THEN** 標準出力は空で、標準エラーに core.db が無いことが出る
