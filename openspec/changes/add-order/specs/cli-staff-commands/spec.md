# Spec Delta

## MODIFIED Requirements

### Requirement: 管理者の data の形
管理者を返すコマンドの --json は、管理者を次の項目を持つオブジェクトで出さなければならない（MUST）。id（内部 ID の文字列）・displayNumber（例 "S-000001"）・email・name・status（"active" か "suspended"）・permissions（権限の名前の配列）・suspendedAt・suspensionReason・createdAt・updatedAt・createdBy・updatedBy。permissions は member:read・member:write・order:read・order:write・order:cancel-approve・item:write・notice:write・staff:admin の順に、持つものだけを並べなければならない（MUST）。公開 ID・パスワード・ハッシュは出してはならない（MUST NOT）。

#### Scenario: 最初の管理者を出す
- **WHEN** staff bootstrap --json で最初の管理者を作る
- **THEN** permissions は 8 種類全てが決めた順に並び、createdBy は {"type":"staff","id":"<自分の内部 ID>"} になる

#### Scenario: 権限の無い管理者
- **WHEN** 権限を付けずに登録した管理者を staff get --json で出す
- **THEN** permissions は空の配列になる
