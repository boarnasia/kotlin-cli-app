# --json の契約

dashboard が `core-cli ... --json` の標準出力を読む。ここに書いた形は互換性を保って変える（項目を足すのは可、名前を変える・消す・型を変えるのは `contractVersion` を上げる）。

`error.code` と終了コードは、`contractVersion` を上げずに今後増えることがある。呼び出し側は、知らない `error.code` や終了コードを失敗として扱う。

## 共通の形

出力は 1 行の JSON オブジェクト。成功も失敗も標準出力に出す。

```json
{"contractVersion":1,"ok":true,"command":"db status","data":{ ... }}
```

```json
{"contractVersion":1,"ok":false,"command":"db status","error":{"code":"DB_NOT_FOUND","message":"..."}}
```

| 項目 | 型 | 中身 |
| --- | --- | --- |
| `contractVersion` | number | この契約の版。今は `1` |
| `ok` | boolean | 成功なら `true` |
| `command` | string \| null | 実行したコマンド（例 `"db status"`）。解析前に失敗したときは `null` |
| `data` | object | 成功のときだけ。コマンドごとの中身 |
| `error.code` | string | 失敗のときだけ。下の表のどれか |
| `error.message` | string | 人向けの説明。文言は変わりうるので、判定には `code` を使う |

- 値の無い項目は省かずに `null` で出す
- 時刻は UTC の ISO-8601 文字列（ミリ秒 3 桁、例 `"2026-11-16T03:00:00.000Z"`）
- 内部 ID は桁あふれを避けるため文字列で出す。表示用の番号・公開 ID と合わせて 3 つとも出す
- 金額は円単位の整数

## 終了コード

| 終了コード | `error.code` | 意味 |
| --- | --- | --- |
| 0 | - | 成功 |
| 1 | `INTERNAL` | 想定外のエラー |
| 2 | `USAGE` | 引数の誤り、確認の `--yes` が無いなど |
| 3 | `NOT_FOUND` | 指定した対象が無い |
| 3 | `DB_NOT_FOUND` | core.db が無い |
| 4 | `CONFLICT` | 状態が合わない、重複、在庫不足など |
| 5 | `VALIDATION` | 入力の値が正しくない |
| 6 | `AUTH_FAILED` | パスワードの照合に失敗した |
| 7 | `FORBIDDEN` | `--actor` の管理者がいない・停止中・要る権限が無い |

## --actor

`db` のコマンド・`user authenticate`・`staff authenticate`・`staff bootstrap` を除く全てのコマンドは、閲覧を含めて `--actor` に操作した管理者を必須で取る。管理者の内部 ID か表示用の番号（例 `S-000001`）を渡す。

- 無いとき、どちらの形でもないとき（`M-` の番号や UUID など）は `USAGE`（2）
- いない・停止中・要る権限が無い管理者は `FORBIDDEN`（7）。対象がいるかどうかより先に確かめるので、権限が無ければ対象がいなくても `NOT_FOUND` にはならない
- dashboard は、読む権限（`member:read` など）だけを持つ管理者を `staff create` で作り、その番号を渡す

## コマンドごとの data

### db migrate / db reset

```json
{
  "path": "/abs/path/core.db",
  "appliedCount": 1,
  "applied": [
    {"version": "1", "description": "display number counter", "state": "applied", "installedAt": "2026-11-16T03:00:00.000Z"}
  ],
  "currentVersion": "1"
}
```

### db status

```json
{
  "path": "/abs/path/core.db",
  "currentVersion": "1",
  "pendingCount": 0,
  "migrations": [
    {"version": "1", "description": "display number counter", "state": "applied", "installedAt": "2026-11-16T03:00:00.000Z"}
  ]
}
```

`state` は `applied` / `pending` / `failed` / `other`。未適用の行は `installedAt` が `null`。

### 会員（user のコマンドが返す会員の形）

```json
{
  "id": "115575710177275904",
  "displayNumber": "M-000001",
  "publicId": "eb1dda65-3f16-40e7-8400-421961440b41",
  "email": "taro@example.com",
  "name": "山田 太郎",
  "status": "active",
  "suspendedAt": null,
  "suspensionReason": null,
  "createdAt": "2026-11-16T03:00:00.000Z",
  "updatedAt": "2026-11-16T03:00:00.000Z",
  "createdBy": {"type": "staff", "id": "115575700000000000"},
  "updatedBy": {"type": "staff", "id": "115575700000000000"}
}
```

- `status` は `active` / `suspended`。停止中だけ `suspendedAt` が値を持つ。`suspensionReason` は停止中でも理由が無ければ `null`
- `createdBy` / `updatedBy` は操作した人。`type` は `staff`（管理者）か `member`（会員本人。mypage で自分で登録・変更したとき）で、`id` はその内部 ID の文字列。人向けの表示では S-・M- の番号で出すが、`--json` は内部 ID のまま
- パスワードとそのハッシュは出さない

### user create / get / update / suspend / activate / set-password

data は会員 1 人（上の形）。

### user list

```json
{"items": [ /* 会員 */ ], "total": 5, "limit": 50, "offset": 0}
```

`items` は表示用の番号の昇順。`total` は絞り込みに当たる全件数。

### user authenticate

```json
{"authenticated": true, "user": { /* 会員 */ }}
```

失敗は理由（いない・停止中・パスワード違い）を区別せず、全て `AUTH_FAILED`（終了コード 6）。

### 管理者（staff のコマンドが返す管理者の形）

```json
{
  "id": "115575700000000000",
  "displayNumber": "S-000001",
  "email": "admin@example.com",
  "name": "最初の管理者",
  "status": "active",
  "permissions": ["member:read", "member:write", "order:read", "order:write", "order:cancel-approve", "item:write", "notice:write", "staff:admin"],
  "suspendedAt": null,
  "suspensionReason": null,
  "createdAt": "2026-11-17T03:00:00.000Z",
  "updatedAt": "2026-11-17T03:00:00.000Z",
  "createdBy": {"type": "staff", "id": "115575700000000000"},
  "updatedBy": {"type": "staff", "id": "115575700000000000"}
}
```

- 管理者は公開 ID を持たないので `publicId` は無い。パスワードとそのハッシュは出さない
- `permissions` は持つ権限だけを、上の例の順（8 種類の決めた順）に並べた配列。権限が無ければ `[]`
- `member:write` は `member:read` を、`order:write` と `order:cancel-approve` は `order:read` を含む（配列には付けた権限だけが出る）
- `status`・`suspendedAt`・`suspensionReason`・`createdBy`・`updatedBy` は会員と同じ。`staff bootstrap` で作った管理者は、登録した人が自分自身

### staff bootstrap / create / get / update / suspend / activate / set-password

data は管理者 1 人（上の形）。`staff bootstrap` は管理者が 1 人でもいれば `CONFLICT`（4）。

### staff list

```json
{"items": [ /* 管理者 */ ], "total": 2, "limit": 50, "offset": 0}
```

### staff authenticate

```json
{"authenticated": true, "staff": { /* 管理者 */ }}
```

失敗は理由（いない・停止中・パスワード違い）を区別せず、全て `AUTH_FAILED`（終了コード 6）。

### 商品（item のコマンドが返す商品の形）

```json
{
  "id": "115900000000000000",
  "displayNumber": "I-000001",
  "name": "ノート",
  "description": "A5 の方眼",
  "price": 300,
  "status": "published",
  "publishedAt": "2026-11-17T03:05:00.000Z",
  "deletedAt": null,
  "stock": {"onHand": 10, "reserved": 3, "available": 7},
  "createdAt": "2026-11-17T03:00:00.000Z",
  "updatedAt": "2026-11-17T03:05:00.000Z",
  "createdBy": {"type": "staff", "id": "115575700000000000"},
  "updatedBy": {"type": "staff", "id": "115575700000000000"}
}
```

- 商品は公開 ID を持たないので `publicId` は無い
- `price` は円の整数。0〜1,000,000,000
- `status` は `unpublished`（非公開）・`published`（公開）・`deleted`（削除済み）のどれか。登録した直後は `unpublished`
- `publishedAt` は公開中だけ値を持つ（最後に公開した時刻）。`deletedAt` は削除済みだけ値を持つ
- `description` は無ければ `null`
- `stock` は読んだ時点の在庫。`available`（注文可能数）は `onHand − reserved`。在庫の変動では `updatedAt`・`updatedBy` は変わらない

### item create / get / update / publish / unpublish / delete

data は商品 1 つ（上の形）。

- 許されない遷移（公開中の `publish`、非公開の `unpublish`）と、削除済みの商品への操作は `CONFLICT`（4）
- `item delete` は `--yes` が無ければ `USAGE`（2）。引当のある商品（受付中の注文がある商品）は `CONFLICT`（4）
- `item update` は `--name`・`--price`・`--description` のどれも無ければ `VALIDATION`（5）。`--description ""` は説明を消す
- 商品の指定は内部 ID か `I-` の番号。ほかの種類の番号と UUID は `VALIDATION`（5）

### item list

```json
{"items": [ /* 商品 */ ], "total": 2, "limit": 50, "offset": 0}
```

`--status` を省いたら削除済みを除く。

### 在庫（stock show が返す形）

```json
{"itemId": "115900000000000000", "displayNumber": "I-000001", "onHand": 10, "reserved": 3, "available": 7, "updatedAt": "2026-11-17T03:05:00.000Z"}
```

### 在庫の変動

```json
{
  "id": "115900000000000001",
  "itemId": "115900000000000000",
  "kind": "adjust",
  "onHandDelta": 10,
  "reservedDelta": 0,
  "onHandAfter": 10,
  "reservedAfter": 0,
  "reason": "入荷",
  "orderId": null,
  "createdAt": "2026-11-17T03:01:00.000Z",
  "createdBy": {"type": "staff", "id": "115575700000000000"}
}
```

- `kind` は `adjust`（手動の調整、在庫数だけ）・`reserve`（注文の引当、引当だけ +）・`release`（キャンセルの承認による戻し、引当だけ −）・`ship`（注文の完了による出荷、在庫数と引当を同じ数だけ −）のどれか
- `onHandAfter`・`reservedAfter` は変動した後の在庫数と引当
- `reason` は調整の理由（調整では必ずある）。`orderId` は引当・戻し・出荷のときの注文の内部 ID の文字列で、調整では `null`

### stock adjust

```json
{"stock": { /* 在庫 */ }, "movement": { /* 在庫の変動 */ }}
```

- `--delta` は 0 以外の整数（負の数で減らす）。整数でなければ `USAGE`（2）、0 は `VALIDATION`（5）
- 調整した後の在庫数が引当を下回るときと、削除済みの商品は `CONFLICT`（4）

### stock history

```json
{"items": [ /* 在庫の変動、新しい順 */ ], "total": 2, "limit": 50, "offset": 0}
```
