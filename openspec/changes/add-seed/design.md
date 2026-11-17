# Design

## Context

投入に要る操作は、全て core の既存のユースケースにある（`StaffService.bootstrap`・`register`・`suspend`、`UserService.register`・`suspend`、`ItemService.create`・`publish`・`delete`、`StockService.adjust`、`OrderService.place` と状態の遷移、`NoticeService.create`・`end`・`delete`）。どれも 1 回の呼び出しが 1 つのトランザクションで、権限の確認もその中で行う。CLI のコマンドは `StaffActorCommand`・`DbCommand`・`respond` で書く（cli-conventions）。

## Goals / Non-Goals

**Goals:**

- 規則の確認を core のユースケースに任せたまま投入する（DB に直接書かない）
- `db reset --yes` → `seed all` で、いつ実行しても同じ形のデータになる

**Non-Goals:**

- 既にあるデータへの追記・差分の反映（空のモジュールにだけ投入する）
- 過去の日付への投入（作成・注文・在庫の時刻は全て実行した時刻）。dashboard の期間別の表示に要るようになったら後の課題として扱う。core のユースケースは Clock を受け取るので、CLI だけで足せる
- 会員本人としての登録・注文（`Registrant.Self`・`MemberOrderService`）。全て管理者の操作として投入する
- 定義ごとに操作した管理者を変えること

## Decisions

### 定義ファイルは JSON で、モジュールごとに 1 つ

kotlinx.serialization の JSON（既に `--json` の出力に使っている）で読む。`ignoreUnknownKeys` は付けず、知らない項目を誤りにする（綴りの誤りを黙って無視しないため）。

- 代わりの案: YAML（kaml など） → 依存が増え、版を確かめる手間も増えるので採らない。コメントが書けない点は README で補う
- 代わりの案: 全てを 1 つのファイルにまとめる → `seed item` だけを別の内容で試すときに、ファイルを分けたほうが差し替えやすいので採らない

形の例（item.json）:

```json
{
  "items": [
    {"name": "ノート", "description": "A5 の方眼", "price": 300, "stock": 20, "status": "published"},
    {"name": "旧型のペン", "price": 150, "status": "deleted"}
  ]
}
```

order.json の操作の並び:

```json
{"orders": [{"member": "member01@example.com", "lines": [{"item": "ノート", "quantity": 2}],
             "steps": [{"op": "request-cancel", "reason": "数を間違えた"}, {"op": "approve-cancel"}]}]}
```

### 既定の定義は CLI に同梱し、`--dir` で差し替える

`apps/cli/src/main/resources/seed/` に置き、クラスパスから読む。`installDist` した CLI をどこに複製しても `seed all` だけで動く。`--dir` を付けたら、そのディレクトリの同じ名前のファイルを読む（同梱のものとは混ぜない）。

- 代わりの案: リポジトリ直下の `seed/` をカレントディレクトリから読む → 複製した CLI ではファイルも複製する必要があるので採らない

### パスワードは定義ファイルに平文で書く

架空の人（`@example.com`）の、デモ用のアカウントだけを書く。リポジトリは公開済みなので、値はほかで使わない、デモ用とわかるもの（`demo-pass-001` など）にする。`user authenticate`・`staff authenticate` を試すときに、人ごとのパスワードがわかっている必要がある。既存の「パスワードを引数の値で受け取らない」はシェルの履歴とプロセスの一覧に残さないためで、ファイルはその対象ではない。README と定義ファイルの置き場所に、実在のパスワードを書かないことを書く。

- 代わりの案: 定義ファイルにはパスワードを書かず、`--password-stdin` で受けた 1 つを全員に付ける → 人ごとに変えられないので採らない

### 空のモジュールにだけ投入する

各モジュールに 1 件でもデータがあれば CONFLICT にする。判定は表示用の番号のカウンタ（`display_number_counter.last_value`）が 0 かどうかで行う。番号は登録と同じトランザクションで進み、取り消されれば戻り、論理削除しても再利用しないので、「1 度でも登録したか」と一致する。この読み取りのために `lib/core/db` の `DisplayNumbers` に、最後に払い出した値を読む関数を 1 つ足す（権限は確かめない。`ActorNumbers` と同じ扱い）。

- 代わりの案: 定義のメールアドレス・名前で既にあるものを飛ばす → 注文には自然な鍵が無く、定義と DB の内容がずれたときの振る舞いが複雑になるので採らない
- 代わりの案: 各サービスの一覧の total で数える → 削除済みを含めるための絞り込みと、閲覧の権限がモジュールごとに違い、`seed all` では管理者がいない時点で確かめられないので採らない

`seed all` は、書き始める前に 5 つのカウンタが全て 0 であることを確かめる（会員は自分で登録できるので、管理者がいなくても会員がいることがある）。確かめてから書き始めるまでの間に他から書かれることは、試作では考えない。

### 書く前に確かめるのは形と参照だけ、規則は core に任せる

ユースケースは 1 件ずつ別のトランザクションなので、seed 全体を 1 つのトランザクションにはできない。書く前に CLI が確かめるのは、定義ファイルの形（JSON の型・要る項目・知らない項目・選択肢の値・期間の書き方）と、参照（注文の会員のメールアドレスと明細の商品名が解決できるか）だけにする。状態の遷移・在庫の数・公開の日時・値の規則は CLI に写さず、core のユースケースに任せる。規則を 2 か所に持たないため。

core で失敗したら、その種類の終了コードで止め、「order.json の 3 件目」のように位置を添え、それまでに書いた分が残っていることと、`db reset --yes` のあとにやり直すことを案内する。

- 代わりの案: core に seed 用の一括のユースケースを足して 1 つのトランザクションにする → 規則の確認を全て複製するか、各ユースケースを DSLContext を受け取る形に割る必要があり、試作の seed のためには大きすぎるので採らない

### 参照はメールアドレスと商品名で行う

注文の会員はメールアドレス（一意）、明細の商品は名前で参照する。表示用の番号は投入の順番や既にあるデータで変わりうるので使わない。`seed order` だけを実行するときは、DB の削除済みでない商品から名前がちょうど 1 つに当たるものを使う（当たらない・複数当たるなら VALIDATION）。会員は core の `UserRepository.findByEmail` で引く（`--actor` に member:read が無くても引けるように。注文の権限は `place` が確かめる）。`seed all` では、同じ実行で登録した会員と商品をそのまま使う。

### `--actor` の扱い

`seed staff` は `StaffService.bootstrap` で最初の管理者を作り、その `Actor.Staff` で残りを `register`（と `suspend`）する。`seed all` はその管理者を user 以降に渡す。どちらも `--actor` を取らない（取る相手がまだいない）。`seed user|item|order|notice` は `StaffActorCommand` を継ぎ、権限の確認は各ユースケースに任せる。

### お知らせの日時は実行した時刻からの相対

`start`・`end` は `java.time.Duration` で読める ISO-8601 の期間（`-P3D`・`PT12H`）。絶対の日時で書くと、日が経つと予約が公開中に、公開中が終了に変わり、既定の定義の意味が崩れる。終了したお知らせは、開始を過去・終了を未来で作り、`"endNow": true` で `end` を呼んで作る（終了は未来でなければならない、core-notice）。

### 出力

data は `{"modules":[{"module":"staff","created":[{"id":"...","displayNumber":"S-000001","publicId":null}, ...]}, ...]}`。人向けは `staff: 7 件（S-000001〜S-000007）` を 1 行ずつ。

### 既定の定義の中身

- 管理者 7 人: 最初の管理者（全ての権限）、会員担当（member:write）、商品とお知らせの担当（item:write・notice:write）、注文担当（order:read・order:write）、承認担当（order:read・order:cancel-approve）、dashboard 用（member:read・order:read）、停止中の 1 人
- 会員 10 人（うち 1 人は停止中）
- 商品 8 件（公開 6・非公開 1・削除済み 1）。初期在庫は 0〜50
- 注文 12 件: 受付済み・キャンセル申請中・キャンセル済み・完了・却下を経た受付済み・却下を経た完了を含む
- お知らせ 6 件: 下書き・予約・公開中（終了あり・なし）・終了・削除済み

## Risks / Trade-offs

- [規則の誤り（遷移に合わない操作・在庫不足など）は書いている途中で見つかり、中途半端なデータが残る] → エラーに位置と書いた分が残ることを出し、`db reset --yes` でやり直してもらう。同梱の定義はテストで `seed all` が通ることを確かめる
- [平文のパスワードがリポジトリと配布物に入る] → 架空のデモ用の値だけにし、README に書く
- [Argon2id のハッシュは 1 人あたり時間がかかる] → 17 人程度なので数秒で済む
