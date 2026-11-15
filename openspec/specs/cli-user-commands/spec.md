# cli-user-commands Specification

## Purpose
管理者が CLI から会員を登録・確認・変更・停止・再開し、パスワードの設定と照合を行えるようにし、dashboard がその結果を --json で読めるようにする。

## Requirements

### Requirement: 会員の data の形
会員を返すコマンドの --json は、会員を次の項目を持つオブジェクトで出さなければならない（MUST）。id（内部 ID の文字列）・displayNumber（例 "M-000001"）・publicId（UUID の文字列）・email・name・status（"active" か "suspended"）・suspendedAt・suspensionReason・createdAt・updatedAt・createdBy・updatedBy。createdBy と updatedBy は操作した人を表すオブジェクトで、type（"staff" か "member"）と id（内部 ID の文字列）を持たなければならない（MUST）。パスワードとそのハッシュは出してはならない（MUST NOT）。

#### Scenario: 有効な会員を出す
- **WHEN** 有効な会員を user get --json で出す
- **THEN** data に上の 12 項目が全て入り、suspendedAt と suspensionReason は null になる

#### Scenario: 管理者が登録した会員
- **WHEN** user create --actor 7 で登録した会員を --json で出す
- **THEN** createdBy と updatedBy は {"type":"staff","id":"7"} になる

#### Scenario: mypage で自分で登録した会員
- **WHEN** 会員が自分で登録し、内部 ID 100 が払い出された会員を user get --json で出す
- **THEN** createdBy は {"type":"member","id":"100"} になる

#### Scenario: パスワードを出さない
- **WHEN** user create・user set-password・user authenticate を --json で実行する
- **THEN** 出力にパスワードもハッシュも含まれない

### Requirement: 書き込みのコマンドは --actor に内部 ID を取る
user create・update・suspend・activate・set-password は --actor に管理者の内部 ID（正の整数）を必須で取らなければならない（MUST）。無いときや正の整数でないときは、書き込まずに USAGE（終了コード 2）で失敗させなければならない（MUST）。user list・get・authenticate は --actor を取ってはならない（MUST NOT）。

#### Scenario: --actor が無い
- **WHEN** user create を --actor 無しで実行する
- **THEN** 終了コード 2 で失敗し、会員は作られない

#### Scenario: --actor が番号の形
- **WHEN** user suspend に --actor S-000001 を付けて実行する
- **THEN** 終了コード 2 で失敗する

### Requirement: 会員は 3 種類の ID のどれでも指定できる
会員を 1 人指定するコマンド（get・update・suspend・activate・set-password）は、位置引数で内部 ID・表示用の番号・公開 ID のどれでも受け付けなければならない（MUST）。どの形でもなければ USAGE（2）、会員以外の種類の番号なら VALIDATION（5）、該当する会員がいなければ NOT_FOUND（3）で失敗させなければならない（MUST）。

#### Scenario: 番号で指定する
- **WHEN** user get M-000001 --json を実行する
- **THEN** 表示用の番号が M-000001 の会員が data に出る

#### Scenario: いない会員
- **WHEN** user get M-999999 --json を実行する
- **THEN** 終了コード 3 で、error.code は NOT_FOUND になる

### Requirement: パスワードは引数の値で受け取らない
パスワードは、コマンドラインの引数の値として受け取ってはならない（MUST NOT）。シェルの履歴やプロセスの一覧に残るため。--password-stdin を付けたら標準入力の 1 行目（末尾の改行だけを除く）を使い、付けないときは端末で表示しない入力を 2 回求めて一致を確かめなければならない（MUST）。端末が無く --password-stdin も無ければ USAGE で失敗させなければならない（MUST）。

#### Scenario: 標準入力で渡す
- **WHEN** echo 'correct horse' | user set-password M-000001 --password-stdin --actor 1 を実行する
- **THEN** パスワードは "correct horse" に変わる

#### Scenario: 端末も --password-stdin も無い
- **WHEN** 標準入力が端末でない状態で、--password-stdin を付けずに user create を実行する
- **THEN** 終了コード 2 で失敗し、会員は作られない

### Requirement: user create は会員を登録する
user create は --email・--name・パスワード・--actor を受け取り、有効な会員を登録して、その会員を data に出さなければならない（MUST）。内部 ID を作るため、CORE_APP_ID（.env か環境変数）が 0〜31 の整数でなければ USAGE で失敗させなければならない（MUST）。メールアドレスの重複は CONFLICT（4）、値の誤りは VALIDATION（5）とする。

#### Scenario: 登録する
- **WHEN** user create --email taro@example.com --name 太郎 --password-stdin --actor 1 --json を実行する
- **THEN** 終了コード 0 で、status が "active"、displayNumber が "M-" で始まる会員が data に出る

#### Scenario: CORE_APP_ID が無い
- **WHEN** CORE_APP_ID を設定せずに user create を実行する
- **THEN** 終了コード 2 で失敗し、CORE_APP_ID が要ることが示される

### Requirement: user list は会員の一覧を返す
user list は --status（active か suspended）・--query（メールアドレスか名前の部分一致）・--limit（1〜500、既定 50）・--offset（0 以上、既定 0）を受け付けなければならない（MUST）。--json の data は items（会員の配列、表示用の番号の昇順）・total（絞り込みに当たる全件数）・limit・offset でなければならない（MUST）。

#### Scenario: 2 件ずつ見る
- **WHEN** 会員が 5 人いるときに user list --limit 2 --offset 2 --json を実行する
- **THEN** items は 3 人目と 4 人目、total は 5、limit は 2、offset は 2 になる

#### Scenario: 範囲の外の件数
- **WHEN** user list --limit 501 を実行する
- **THEN** 終了コード 5 で失敗する

### Requirement: user update は名前とメールアドレスを変える
user update は --email と --name の片方か両方を受け取り、変えた後の会員を data に出さなければならない（MUST）。どちらも無ければ VALIDATION（5）、メールアドレスの重複は CONFLICT（4）で失敗させなければならない（MUST）。

#### Scenario: 何も指定しない
- **WHEN** user update M-000001 --actor 1 を --email も --name も無しで実行する
- **THEN** 終了コード 5 で失敗し、会員は変わらない

### Requirement: user suspend と user activate は状態を変える
user suspend は任意の --reason（500 文字以内）を取り、有効な会員を停止しなければならない（MUST）。user activate は停止中の会員を再開しなければならない（MUST）。どちらも変えた後の会員を data に出し、許されない遷移は CONFLICT（4）で失敗させなければならない（MUST）。

#### Scenario: 停止中の会員を停止する
- **WHEN** 停止中の会員に user suspend --actor 1 --json を実行する
- **THEN** 終了コード 4 で、error.code は CONFLICT になる

### Requirement: user authenticate はパスワードを照合する
user authenticate は --email とパスワードを受け取り、--actor を取らずに照合しなければならない（MUST）。成功したら data は authenticated（true）と user（会員）でなければならない（MUST）。失敗したら、理由（いない・停止中・パスワード違い）を区別せずに AUTH_FAILED（終了コード 6）で失敗させなければならない（MUST）。

#### Scenario: 照合に成功する
- **WHEN** 正しいパスワードで user authenticate --email taro@example.com --password-stdin --json を実行する
- **THEN** 終了コード 0 で、data.authenticated は true、data.user に会員が出る

#### Scenario: 停止中の会員
- **WHEN** 停止中の会員を正しいパスワードで照合する
- **THEN** 終了コード 6 で、パスワードが違うときと同じ error が出る

### Requirement: 人向けの表示
--json を付けないとき、会員 1 人は項目ごとの行で、一覧は 1 人 1 行（番号・状態・メールアドレス・名前）と全件数で表示しなければならない（MUST）。時刻は JST で表示しなければならない（MUST）。

#### Scenario: 一覧を人向けに出す
- **WHEN** user list を --json 無しで実行する
- **THEN** 1 人 1 行で番号・状態・メールアドレス・名前が並び、最後に全件数が出る
