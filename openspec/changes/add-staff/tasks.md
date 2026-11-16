# Tasks

kotlin-core-app の change `add-staff` の実装（lib/core/staff と UserService の修正）が終わってから始める。kotlin-core-app の design.md の Open Questions は推奨案で書いている。

## 1. 共通の仕組み

- [ ] 1.1 `ErrorCode` に FORBIDDEN（7）を足して core の種類と対応させ、終了コードと error.code をテストで確かめる
- [ ] 1.2 `--actor` の基底を作り直し、内部 ID と S- の番号を受け付けること、会員の番号と UUID が終了コード 2、いない・停止中の管理者が終了コード 7 になることをテストで確かめる

## 2. user のコマンドの修正

- [ ] 2.1 user list・user get に `--actor` を足し、既存の `UserCommandsTest` を管理者を作ってから使う形に直して、閲覧の権限だけでは書き込めない（終了コード 7）ことをテストで確かめる

## 3. staff のコマンド

- [ ] 3.1 管理者の data の形を作り、12 項目と permissions の順、公開 ID とパスワードが出ないことをテストで確かめる
- [ ] 3.2 staff bootstrap を足し、空の DB で 7 種類の権限を持つ S-000001 ができること、2 回目が終了コード 4 になることをテストで確かめる
- [ ] 3.3 staff create・list・get を足し、権限の指定・知らない権限の終了コード 2・番号での取得・一覧の data の形をテストで確かめる
- [ ] 3.4 staff update を足し、--grant と --revoke・同じ権限を両方に指定したときの終了コード 5・自分から staff:admin を外すときの終了コード 4 をテストで確かめる
- [ ] 3.5 staff suspend・activate を足し、自分の停止と最後の staff:admin の管理者の停止が終了コード 4 になることをテストで確かめる
- [ ] 3.6 staff set-password・authenticate を足し、照合の成功と、3 つの失敗の理由が同じ AUTH_FAILED になることをテストで確かめる
- [ ] 3.7 人向けの表示を足し、一覧が 1 人 1 行で権限が出ることをテストで確かめる

## 4. 文書と動作の確認

- [ ] 4.1 管理者の data の形・FORBIDDEN（7）・閲覧の `--actor` を `docs/json-contract.md` と `README.md` に書く
- [ ] 4.2 `installDist` した core-cli で、db reset → staff bootstrap → staff create → user create → 権限の無い操作（終了コード 7）→ staff suspend → 停止中の管理者の --actor（終了コード 7）を順に実行し、出力と終了コードが仕様どおりであることを確かめる

## Workflow follow-up

- kotlin-core-app の change `add-staff` と合わせて archive し、`openspec/specs/` に cli-staff-commands と各仕様の修正が反映されたことを確かめる
