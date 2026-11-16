# Design

## Context

item・order と同じく、`StaffActorCommand`・`respond`・`ActorLabels` で書く。規則は core の `NoticeService` が持つ。

## Goals / Non-Goals

**Goals:** 公開の日時を人が打ちやすい形で受け取り、--json は UTC で出す

**Non-Goals:** mypage 向けの `PublicNoticeService` のコマンド（管理者は `notice visible --at` で同じものを確かめる）

## Decisions

### 時差の無い日時は日本時間として読む

運用する人は日本時間で考えるので、`2026-12-01T09:00` を JST として読む。時差を付ければ（`Z`・`+09:00`）そのとおりに読む。日付だけ（`2026-12-01`）は受け付けない（その日の 0 時か終わりかが曖昧なため）。

### 消すオプションを分ける

`--start ""` で消す形にすると、空の文字列と日時の誤りを区別しにくいので、`--clear-start`・`--clear-end` にする。同じ項目に指定と消去の両方を付けたら USAGE。

### status は実行した時刻で計算して出す

dashboard が日時から計算し直さなくてよいように出す。同じお知らせでも、実行する時刻で値が変わる。

### notice delete に --yes を求める

戻す操作が無いので、item delete と同じく --yes を求める。

## Risks / Trade-offs

- [status が実行のたびに変わりうる] → docs/json-contract.md に、実行した時刻で計算した値だと書く
