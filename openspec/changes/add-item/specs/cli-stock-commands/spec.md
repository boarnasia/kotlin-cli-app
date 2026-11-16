# Spec Delta

## Purpose

管理者が CLI から商品の在庫を確認し、入荷や棚卸しの差を手動で調整し、在庫が変わった理由を履歴でたどれるようにする。

## ADDED Requirements

### Requirement: 在庫の data の形
stock show と stock adjust の在庫は、itemId（内部 ID の文字列）・displayNumber（商品の I- の番号）・onHand・reserved・available（どれも整数）・updatedAt を持つオブジェクトで出さなければならない（MUST）。available は onHand − reserved でなければならない（MUST）。

#### Scenario: 在庫を見る
- **WHEN** 在庫数 10・引当 3 の商品に stock show I-000001 --actor S-000001 --json を実行する
- **THEN** data は onHand 10・reserved 3・available 7 になる

### Requirement: 在庫の変動の data の形
在庫の変動は、id（内部 ID の文字列）・itemId・kind（"adjust"・"reserve"・"release"・"ship" のどれか）・onHandDelta・reservedDelta・onHandAfter・reservedAfter（どれも整数）・reason（無ければ null）・orderId（注文の内部 ID の文字列。無ければ null）・createdAt・createdBy を持つオブジェクトで出さなければならない（MUST）。

#### Scenario: 調整の変動
- **WHEN** stock adjust で在庫数を +10 する
- **THEN** 変動の kind は "adjust"、onHandDelta は 10、reservedDelta は 0、orderId は null になる

### Requirement: stock adjust は在庫数を増減する
stock adjust は商品・--delta（0 以外の整数。負の数も受け付ける）・--reason・--actor を受け取り、data に {stock, movement}（調整した後の在庫と、足した変動）を出さなければならない（MUST）。--delta が整数でなければ USAGE（2）、0 や理由の誤りは VALIDATION（5）、在庫数が引当を下回る調整と削除済みの商品は CONFLICT（4）、item:write の無い管理者は FORBIDDEN（7）とする。内部 ID を作るため、CORE_APP_ID が要る。

#### Scenario: 入荷する
- **WHEN** stock adjust I-000001 --delta 10 --reason 入荷 --actor S-000001 --json を実行する
- **THEN** 終了コード 0 で、data.stock.onHand が 10 増え、data.movement.reason が "入荷" になる

#### Scenario: 引当を下回る
- **WHEN** 在庫数 5・引当 3 の商品に --delta -3 で stock adjust を実行する
- **THEN** 終了コード 4 で失敗し、在庫は変わらない

### Requirement: stock show と stock history は在庫と履歴を返す
stock show は在庫を、stock history は --limit・--offset を受け取り data に {items, total, limit, offset}（items は変動の新しい順）を出さなければならない（MUST）。どちらも、権限を問わず有効な管理者なら実行できなければならない（MUST）。削除済みの商品の在庫と履歴も見られなければならない（MUST）。

#### Scenario: 履歴を見る
- **WHEN** +10 と -2 の調整をした商品に stock history I-000001 --actor S-000001 --json を実行する
- **THEN** items の 1 つ目が -2 の変動、2 つ目が +10 の変動で、total は 2 になる

### Requirement: 在庫の人向けの表示
人向けの stock history は 1 つの変動を 1 行で、日本時間の時刻・種類・在庫数と引当の増減と変動後の値・理由・操作した人を出さなければならない（MUST）。stock show は在庫数・引当・注文可能数を出さなければならない（MUST）。

#### Scenario: 履歴を見る
- **WHEN** stock history I-000001 --actor S-000001 を --json 無しで実行する
- **THEN** 1 行に 1 つの変動が、新しい順に出る
