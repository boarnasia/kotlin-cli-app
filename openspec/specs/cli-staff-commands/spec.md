# cli-staff-commands Specification

## Purpose
管理者が CLI から最初の管理者を作り、管理者を登録・確認・変更・停止・再開し、権限を付け外しできるようにし、dashboard がその結果を --json で読めるようにする。

## Requirements

### Requirement: 管理者の data の形
管理者を返すコマンドの --json は、管理者を次の項目を持つオブジェクトで出さなければならない（MUST）。id（内部 ID の文字列）・displayNumber（例 "S-000001"）・email・name・status（"active" か "suspended"）・permissions（権限の名前の配列）・suspendedAt・suspensionReason・createdAt・updatedAt・createdBy・updatedBy。permissions は member:read・member:write・order:read・order:cancel-approve・item:write・notice:write・staff:admin の順に、持つものだけを並べなければならない（MUST）。公開 ID・パスワード・ハッシュは出してはならない（MUST NOT）。

#### Scenario: 最初の管理者を出す
- **WHEN** staff bootstrap --json で最初の管理者を作る
- **THEN** permissions は 7 種類全てが決めた順に並び、createdBy は {"type":"staff","id":"<自分の内部 ID>"} になる

#### Scenario: 権限の無い管理者
- **WHEN** 権限を付けずに登録した管理者を staff get --json で出す
- **THEN** permissions は空の配列になる

### Requirement: staff bootstrap は最初の管理者を作る
staff bootstrap は --email・--name・パスワードを受け取り、--actor を取らずに最初の管理者を作らなければならない（MUST）。管理者が 1 人でもいれば CONFLICT（4）で失敗させなければならない（MUST）。内部 ID を作るため、CORE_APP_ID が無いか範囲の外なら USAGE（2）で失敗させなければならない（MUST）。

#### Scenario: 空の DB で作る
- **WHEN** db migrate 直後の core.db で staff bootstrap --email admin@example.com --name 管理者 --password-stdin --json を実行する
- **THEN** 終了コード 0 で、displayNumber が S-000001 の管理者が data に出る

#### Scenario: 2 回目
- **WHEN** 管理者がいる core.db で staff bootstrap を実行する
- **THEN** 終了コード 4 で失敗し、管理者は増えない

### Requirement: staff create は管理者を登録する
staff create は --email・--name・パスワード・--permission（0 回以上繰り返せる）・--actor を受け取り、有効な管理者を登録して data に出さなければならない（MUST）。知らない権限の名前は USAGE（2）、メールアドレスの重複は CONFLICT（4）、値の誤りは VALIDATION（5）とする。

#### Scenario: 権限を付けて登録する
- **WHEN** staff create --email ops@example.com --name 運用 --permission member:read --permission order:read --password-stdin --actor S-000001 --json を実行する
- **THEN** permissions が ["member:read","order:read"] の有効な管理者が data に出る

#### Scenario: 知らない権限
- **WHEN** --permission member:delete を付けて staff create を実行する
- **THEN** 終了コード 2 で失敗する

### Requirement: staff list と staff get は管理者を返す
staff list は user list と同じオプション（--status・--query・--limit・--offset）と data の形（items・total・limit・offset）でなければならない（MUST）。staff get は位置引数で内部 ID か S- の番号を受け付けなければならない（MUST）。どの形でもなければ USAGE（2）、ほかの種類の番号や公開 ID は VALIDATION（5）、いなければ NOT_FOUND（3）とする。

#### Scenario: 番号で取り出す
- **WHEN** staff get S-000001 --actor S-000001 --json を実行する
- **THEN** S-000001 の管理者が data に出る

### Requirement: staff update は属性と権限を変える
staff update は --email・--name・--grant（付ける権限、繰り返せる）・--revoke（外す権限、繰り返せる）の 1 つ以上を受け取り、変えた後の管理者を data に出さなければならない（MUST）。どれも無いときと、同じ権限を --grant と --revoke の両方に指定したときは VALIDATION（5）、自分から staff:admin を外すときと、staff:admin を持つ有効な管理者を 0 人にするときは CONFLICT（4）としなければならない（MUST）。

#### Scenario: 自分から管理の権限を外す
- **WHEN** S-000001 が staff update S-000001 --revoke staff:admin --actor S-000001 を実行する
- **THEN** 終了コード 4 で失敗し、権限は変わらない

### Requirement: staff suspend と staff activate は状態を変える
staff suspend は任意の --reason（500 文字以内）を取り、有効な管理者を停止しなければならない（MUST）。staff activate は停止中の管理者を再開しなければならない（MUST）。許されない遷移・自分の停止・staff:admin を持つ有効な管理者を 0 人にする停止は CONFLICT（4）としなければならない（MUST）。

#### Scenario: 自分を停止する
- **WHEN** staff suspend S-000001 --actor S-000001 を実行する
- **THEN** 終了コード 4 で失敗する

### Requirement: staff set-password と staff authenticate
staff set-password と staff authenticate のパスワードの受け取り方は user のコマンドと同じでなければならない（MUST）。staff authenticate は --actor を取らずに照合し、成功したら data を authenticated（true）と staff（管理者）にしなければならない（MUST）。失敗は理由を区別せずに AUTH_FAILED（6）としなければならない（MUST）。

#### Scenario: 停止中の管理者が照合する
- **WHEN** 停止中の管理者を正しいパスワードで staff authenticate する
- **THEN** 終了コード 6 で、パスワードが違うときと同じ error が出る

### Requirement: 人向けの表示
--json を付けないとき、管理者 1 人は項目ごとの行（権限を含む）で、一覧は 1 人 1 行（番号・状態・メールアドレス・名前・権限）と全件数で表示しなければならない（MUST）。時刻は JST で表示しなければならない（MUST）。

#### Scenario: 一覧を人向けに出す
- **WHEN** staff list --actor S-000001 を --json 無しで実行する
- **THEN** 1 人 1 行で番号・状態・メールアドレス・名前・権限が並び、最後に全件数が出る
