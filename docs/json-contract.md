# --json の契約

dashboard が `core-cli ... --json` の標準出力を読む。ここに書いた形は互換性を保って変える（項目を足すのは可、名前を変える・消す・型を変えるのは `contractVersion` を上げる）。

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
