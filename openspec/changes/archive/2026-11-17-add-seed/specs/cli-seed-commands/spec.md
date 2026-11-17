# Spec Delta

## Purpose

管理者・会員・商品・注文・お知らせを、定義ファイルから決まった内容で core.db に投入し、dashboard や API の試作に使えるデータを手早く揃えられるようにする。

## ADDED Requirements

### Requirement: seed のコマンドと投入の順番
CLI は seed staff・seed user・seed item・seed order・seed notice・seed all を持たなければならない（MUST）。seed all は staff → user → item → order → notice の順に全てを投入しなければならない（MUST）。会員の登録に有効な管理者が、注文に会員と公開中の商品が要るためである。core.db が無ければ DB_NOT_FOUND（3）とする。

#### Scenario: 空の DB に全てを投入する
- **WHEN** db reset --yes の直後に seed all を実行する
- **THEN** 管理者・会員・商品・注文・お知らせが既定の定義ファイルのとおりに投入され、終了コードは 0 になる

#### Scenario: DB が無い
- **WHEN** 存在しない core.db を指定して seed all を実行する
- **THEN** 終了コード 3 で失敗する

### Requirement: 定義ファイルの形と置き場所
定義ファイルは JSON で、モジュールごとに staff.json・user.json・item.json・order.json・notice.json の 1 つずつとしなければならない（MUST）。--dir を省いたら CLI に同梱した既定の定義ファイルを、--dir を付けたらそのディレクトリの同じ名前のファイルを読まなければならない（MUST）。ファイルが無い・JSON として読めない・知らない項目がある・要る項目が無いときは、何も書かずに VALIDATION（5）とする。

#### Scenario: 別のディレクトリの定義ファイルを使う
- **WHEN** 会員 2 人を書いた user.json を置いたディレクトリを --dir に渡して seed user を実行する
- **THEN** その 2 人だけが登録される

#### Scenario: 知らない項目
- **WHEN** item.json の商品に定義に無い項目 colour がある
- **THEN** 終了コード 5 で失敗し、何も書かれない

### Requirement: 最初の管理者は bootstrap で作る
staff.json は、最初の管理者（メールアドレス・名前・パスワード）と、続けて登録する管理者の並び（メールアドレス・名前・パスワード・権限・状態）を持たなければならない（MUST）。seed staff は最初の管理者を staff bootstrap と同じ方法で全ての権限を付けて作り、その管理者として残りを登録しなければならない（MUST）。管理者が 1 人でもいれば何も書かずに CONFLICT（4）とする。

#### Scenario: 管理者を投入する
- **WHEN** 管理者のいない DB で seed staff を実行する
- **THEN** 最初の管理者が S-000001 として全ての権限を持ち、残りの管理者の登録した人は S-000001 になる

#### Scenario: 既に管理者がいる
- **WHEN** staff bootstrap を済ませた DB で seed staff を実行する
- **THEN** 終了コード 4 で失敗し、管理者は増えない

### Requirement: seed staff と seed all は --actor を取らない
seed staff と seed all は --actor を取ってはならない（MUST NOT）。seed all の user 以降は、seed staff で作った最初の管理者として投入しなければならない（MUST）。seed user・seed item・seed order・seed notice は --actor を必須で受け取り、その管理者として投入しなければならない（MUST）。要る権限は各モジュールの登録・変更のコマンドと同じで、無ければ何も書かずに FORBIDDEN（7）とする。

#### Scenario: 権限の無い管理者で商品を投入する
- **WHEN** item:write を持たない管理者を --actor に渡して seed item を実行する
- **THEN** 終了コード 7 で失敗し、商品は増えない

#### Scenario: seed all の操作した人
- **WHEN** 空の DB で seed all を実行する
- **THEN** 会員・商品・注文・お知らせの登録した人は全て S-000001 になる

### Requirement: 既にデータがあるモジュールには投入しない
seed は、投入するモジュールに 1 件でもデータ（停止中・削除済みを含む）があれば、何も書かずに CONFLICT（4）としなければならない（MUST）。seed all は、書き始める前に全てのモジュールが空であることを確かめなければならない（MUST）。同じ定義を 2 回投入して重複させないためである。

#### Scenario: 2 回目の投入
- **WHEN** seed item を済ませた DB でもう一度 seed item を実行する
- **THEN** 終了コード 4 で失敗し、商品は増えない

#### Scenario: 一部だけ入っている DB に seed all
- **WHEN** 商品だけがある DB で seed all を実行する
- **THEN** 終了コード 4 で失敗し、管理者も作られない

### Requirement: 書く前に確かめるのは形と参照だけにする
seed は、書き始める前に、読む定義ファイル全体の形（JSON の型・要る項目・知らない項目・選択肢の値・期間の書き方）と参照（注文の会員のメールアドレスと明細の商品名が、同じ seed の定義か、投入しないモジュールなら DB で、ちょうど 1 つに解決できること）を確かめ、誤りがあれば何も書かずに VALIDATION（5）としなければならない（MUST）。状態の遷移・在庫の数・公開の日時・値の規則は CLI で確かめてはならず（MUST NOT）、core のユースケースに任せる。

#### Scenario: 定義に無い会員を参照する
- **WHEN** order.json の注文が、user.json にも DB にもないメールアドレスの会員を参照する
- **THEN** 終了コード 5 で失敗し、何も書かれない

#### Scenario: 商品名が 2 つに当たる
- **WHEN** item.json に同じ名前の商品が 2 つあり、order.json がその名前を参照する
- **THEN** 終了コード 5 で失敗し、何も書かれない

### Requirement: 途中で失敗したときは書いた分が残る
書いている途中で core が失敗したときは、その失敗の種類の終了コードで止め、それまでに書いた分は残さなければならない（MUST）。エラーの説明には、どの定義ファイルの何件目で失敗したか・それまでに書いた分が残っていること・db reset --yes のあとにやり直すことを含めなければならない（MUST）。

#### Scenario: 値の規則に合わない
- **WHEN** user.json の 3 人目のメールアドレスが形に合わない
- **THEN** 終了コード 5 で失敗し、1〜2 人目は登録されたまま、エラーの説明に user.json の 3 件目であることと db reset --yes が出る

#### Scenario: 在庫が足りない
- **WHEN** 初期在庫 3 の商品を、order.json の 1 件目で 2 個・2 件目で 2 個注文する
- **THEN** 2 件目で終了コード 4 で失敗し、1 件目の注文は残る

### Requirement: 会員と管理者の定義
user.json と staff.json の各人は、メールアドレス・名前・パスワード（平文。ほかで使わないデモ用の値）を持ち、状態（active か suspended、省いたら active）と停止の理由を持てなければならない（MUST）。suspended の人は登録したあとに停止しなければならない（MUST）。管理者は権限（8 種類の名前の並び）を持たなければならない（MUST）。パスワードは core が Argon2id でハッシュにして保存する。

#### Scenario: 停止中の会員を投入する
- **WHEN** status が suspended で停止の理由が「テスト」の会員を投入する
- **THEN** その会員は停止中で、停止の理由は「テスト」になる

### Requirement: 商品と在庫の定義
item.json の各商品は、名前・説明（省ける）・価格・初期在庫（整数、省いたら 0）・状態（unpublished・published・deleted、省いたら unpublished）を持たなければならない（MUST）。初期在庫が 0 でなければ、登録したあとに理由「初期在庫」で在庫を調整し、それから状態を変えなければならない（MUST）。

#### Scenario: 公開中の商品を投入する
- **WHEN** 初期在庫 10・状態 published の商品を投入する
- **THEN** 商品は公開中で、在庫数 10、在庫の履歴に理由「初期在庫」の調整が 1 件ある

### Requirement: 注文の定義と状態
order.json の各注文は、会員（メールアドレス）・明細（商品の名前と数）と、作ったあとに順に行う操作（request-cancel・reject-cancel・approve-cancel・complete、理由を持てる）の並びを持たなければならない（MUST）。商品の名前は、状態が deleted でない商品の中でちょうど 1 つに当たらなければならない（MUST）。操作の並びが状態の遷移に合わなければ、core の CONFLICT（4）で止まる。

#### Scenario: キャンセル済みの注文を投入する
- **WHEN** 操作が request-cancel・approve-cancel の注文を投入する
- **THEN** 注文はキャンセル済みになり、引当は戻り、注文の履歴に作成・申請・承認が残る

#### Scenario: 遷移に合わない操作
- **WHEN** 操作が complete・request-cancel の注文を投入する
- **THEN** request-cancel で終了コード 4 で失敗し、その注文は完了のまま残る

### Requirement: お知らせの定義
notice.json の各お知らせは、題名・本文・公開開始と公開終了（実行した時刻からの ISO-8601 の期間、例 "-P3D"・"PT12H"、省いたら未設定）・今すぐ終了するか・論理削除するかを持たなければならない（MUST）。相対で書くのは、いつ投入しても予約・公開中・終了が同じ並びになるようにするためである。公開の日時の規則は core が確かめる。

#### Scenario: 終了したお知らせを投入する
- **WHEN** 公開開始 "-P7D"・公開終了 "P7D"・endNow が true のお知らせを投入する
- **THEN** そのお知らせの status は "ended" になる

#### Scenario: 過去の公開終了
- **WHEN** 公開終了が "-P1D" のお知らせを投入する
- **THEN** core の VALIDATION で終了コード 5 で失敗し、エラーの説明に notice.json の何件目かが出る

### Requirement: seed の data の形
seed の --json の data は、投入したモジュールを順に並べた modules を持ち、各要素は module（"staff" など）と created（作ったものの id・displayNumber・publicId（無ければ null）の並び）を持たなければならない（MUST）。人向けの表示は、モジュールごとに件数と番号の範囲を 1 行で出す。

#### Scenario: seed item の結果
- **WHEN** 商品 3 件の定義で seed item --actor S-000001 --json を実行する
- **THEN** data.modules は 1 要素で、module は "item"、created は I-000001〜I-000003 の 3 件になる
