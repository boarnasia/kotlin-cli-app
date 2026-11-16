# Spec Delta

## Purpose

管理者が CLI から商品を登録・確認・変更・公開・非公開・削除でき、dashboard がその結果を --json で読めるようにする。

## ADDED Requirements

### Requirement: 商品の data の形
商品を返すコマンドの --json は、商品を次の項目を持つオブジェクトで出さなければならない（MUST）。id（内部 ID の文字列）・displayNumber（例 "I-000001"）・name・description（無ければ null）・price（円の整数）・status（"unpublished"・"published"・"deleted" のどれか）・publishedAt・deletedAt・stock（onHand・reserved・available の 3 つの整数を持つオブジェクト）・createdAt・updatedAt・createdBy・updatedBy。商品は公開 ID を持たないので出してはならない（MUST NOT）。

#### Scenario: 登録した直後の商品
- **WHEN** item create --json で商品を登録する
- **THEN** status は "unpublished"、publishedAt と deletedAt は null、stock は {"onHand":0,"reserved":0,"available":0} になる

### Requirement: 商品の指定
商品を指定する引数は、内部 ID か表示用の番号（I-）を受け付けなければならない（MUST）。どの ID の形でもなければ USAGE（2）、ほかの種類の番号や UUID は VALIDATION（5）、いなければ NOT_FOUND（3）とする。

#### Scenario: 会員の番号を渡す
- **WHEN** item get M-000001 --actor S-000001 --json を実行する
- **THEN** 終了コード 5 で、error.code は VALIDATION になる

### Requirement: item create は商品を登録する
item create は --name・--price・--description（省ける）・--actor を受け取り、非公開の商品を登録して data に出さなければならない（MUST）。--price が整数でなければ USAGE（2）、値の誤り（範囲の外・空の名前）は VALIDATION（5）とする。内部 ID を作るため、CORE_APP_ID が無いか範囲の外なら USAGE（2）とする。item:write の無い管理者は FORBIDDEN（7）とする。

#### Scenario: 登録する
- **WHEN** item create --name "ノート" --price 300 --actor S-000001 --json を実行する
- **THEN** 終了コード 0 で、displayNumber が I-000001、price が 300 の商品が data に出る

#### Scenario: 閲覧だけの管理者
- **WHEN** item:write を持たない管理者を --actor に渡して item create を実行する
- **THEN** 終了コード 7 で失敗し、商品は作られない

### Requirement: item list と item get は商品を返す
item list は --status（unpublished・published・deleted）・--query（名前と説明の部分一致）・--limit・--offset を受け取り、data に {items, total, limit, offset} を出さなければならない（MUST）。--status を省いたら削除済みを除く。item get は商品 1 つを data に出さなければならない（MUST）。どちらも、権限を問わず有効な管理者なら実行できなければならない（MUST）。

#### Scenario: 権限の無い管理者が一覧を見る
- **WHEN** 権限を 1 つも持たない有効な管理者を --actor に渡して item list --json を実行する
- **THEN** 終了コード 0 で、削除済みを除く商品が番号の昇順で出る

### Requirement: item update は属性を変える
item update は --name・--price・--description のうち 1 つ以上と --actor を受け取り、変えた商品を data に出さなければならない（MUST）。どれも無ければ VALIDATION（5）とする。--description "" は説明を消す。削除済みの商品は CONFLICT（4）とする。

#### Scenario: 説明を消す
- **WHEN** item update I-000001 --description "" --actor S-000001 --json を実行する
- **THEN** description が null の商品が data に出る

### Requirement: item publish と item unpublish は公開を切り替える
item publish は非公開の商品を公開に、item unpublish は公開中の商品を非公開にし、data に出さなければならない（MUST）。許されない遷移（公開中の publish、非公開の unpublish、削除済みへの操作）は CONFLICT（4）とする。

#### Scenario: 公開する
- **WHEN** 非公開の I-000001 に item publish I-000001 --actor S-000001 --json を実行する
- **THEN** status が "published" で、publishedAt に時刻が入った商品が data に出る

### Requirement: item delete は商品を論理削除する
item delete は、取り消せない操作なので --yes を必須とし、無ければ USAGE（2）で何もせずに失敗させなければならない（MUST）。削除した商品（status "deleted"、deletedAt に時刻）を data に出さなければならない（MUST）。引当のある商品と削除済みの商品は CONFLICT（4）とする。

#### Scenario: --yes が無い
- **WHEN** item delete I-000001 --actor S-000001 を実行する
- **THEN** 終了コード 2 で失敗し、商品は変わらない

#### Scenario: 引当のある商品
- **WHEN** 引当が 1 以上の商品に item delete --yes を実行する
- **THEN** 終了コード 4 で失敗する

### Requirement: 商品の人向けの表示
人向けの item list は 1 つの商品を 1 行で、番号・状態・価格・在庫数と注文可能数・名前を出し、最後に全件数を出さなければならない（MUST）。item get などは属性を 1 項目 1 行で出し、時刻は日本時間で出さなければならない（MUST）。

#### Scenario: 一覧を見る
- **WHEN** item list --actor S-000001 を --json 無しで実行する
- **THEN** 1 行に 1 つの商品が、番号・状態・価格・在庫数・注文可能数・名前とともに出る
