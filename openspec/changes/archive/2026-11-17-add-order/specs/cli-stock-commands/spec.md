# Spec Delta

## MODIFIED Requirements

### Requirement: 在庫の人向けの表示
人向けの stock history は 1 つの変動を 1 行で、日本時間の時刻・種類・在庫数と引当の増減と変動後の値・理由・注文（引当・戻し・出荷のとき）・操作した人を出さなければならない（MUST）。注文は表示用の番号（O-）で出し、番号が引けなければ内部 ID で出す。--json の orderId は内部 ID のまま変えてはならない（MUST NOT）。stock show は在庫数・引当・注文可能数を出さなければならない（MUST）。

#### Scenario: 履歴を見る
- **WHEN** stock history I-000001 --actor S-000001 を --json 無しで実行する
- **THEN** 1 行に 1 つの変動が、新しい順に出る

#### Scenario: 注文の引当を見る
- **WHEN** O-000001 で引き当てた商品に stock history を --json 無しで実行する
- **THEN** 引当の行に「注文 O-000001」が出る
