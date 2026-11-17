# cli-notice-commands Specification

## Purpose

管理者が CLI からお知らせを管理し、ある時刻に公開中のお知らせを確かめられ、dashboard がその結果を --json で読めるようにする。

## Requirements

### Requirement: お知らせの data の形
お知らせを返すコマンドの --json は、id・displayNumber（例 "N-000001"）・title・body・status（"draft"・"scheduled"・"active"・"ended"、実行した時刻で計算）・publishStartAt・publishEndAt・deletedAt・createdAt・updatedAt・createdBy・updatedBy を持つオブジェクトで出さなければならない（MUST）。

#### Scenario: 下書き
- **WHEN** notice create --title お休み --body 年末年始 --actor S-000001 --json を実行する
- **THEN** status は "draft"、publishStartAt と publishEndAt は null になる

### Requirement: 日時の入力
--start・--end・--at は ISO-8601 の日時を受け取らなければならない（MUST）。時差（Z や +09:00）があればそのとおりに、無ければ日本時間として読まなければならない（MUST）。形が違えば USAGE（2）とする。--json の時刻は UTC で出す。

#### Scenario: 時差の無い入力
- **WHEN** --start 2026-12-01T09:00 で登録する
- **THEN** publishStartAt は "2026-12-01T00:00:00.000Z" になる

### Requirement: お知らせを書き換えるコマンド
notice create は --title・--body・--start（省ける）・--end（省ける）を、notice update は --title・--body・--start・--end・--clear-start・--clear-end のうち 1 つ以上を受け取らなければならない（MUST）（無ければ VALIDATION（5））。notice end は公開中を今すぐ終了し（開始と同じミリ秒でも成功して終了扱いとなり visible に出ない）、notice delete は --yes を必須として論理削除する（--yes が無ければ USAGE（2））。どれも notice:write が要る（無ければ FORBIDDEN（7））。許されない操作は CONFLICT（4）、日時の規則の誤り（登録・変更で終了＝開始も含む）は VALIDATION（5）とする。

#### Scenario: 下書きを終了する
- **WHEN** 下書きのお知らせに notice end を実行する
- **THEN** 終了コード 4 で失敗する

#### Scenario: 権限の無い管理者が登録する
- **WHEN** notice:write を持たない管理者を --actor に渡して notice create を実行する
- **THEN** 終了コード 7 で失敗する

### Requirement: お知らせを見るコマンド
notice get は内部 ID か N- の番号でお知らせを出し、notice list は --status（draft・scheduled・active・ended・deleted）・--limit・--offset を受け取って {items, total, limit, offset} を番号の新しい順で出し、notice visible は --at（省いたら今）に公開中のお知らせを {at, items} で公開開始の新しい順に出さなければならない（MUST）。どれも権限を問わず有効な管理者なら実行できる。いなければ NOT_FOUND（3）。

#### Scenario: 予約を確かめる
- **WHEN** 公開開始 2026-12-01T09:00 のお知らせがあり、notice visible --at 2026-12-01T09:00 --actor S-000001 --json を実行する
- **THEN** items にそのお知らせが出る

### Requirement: お知らせの人向けの表示
人向けの notice list と visible は 1 つのお知らせを 1 行で、番号・状態・公開の期間（JST）・題名を出さなければならない（MUST）。notice get などは本文を含めて 1 項目 1 行で、操作した人を表示用の番号で出さなければならない（MUST）。

#### Scenario: 一覧を見る
- **WHEN** notice list --actor S-000001 を --json 無しで実行する
- **THEN** 1 行に 1 つのお知らせが、番号・状態・JST の期間・題名とともに出る
