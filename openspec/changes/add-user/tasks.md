# Tasks

kotlin-core-app の change `add-user` の実装（lib/core/user・パスワード）が終わってから始める。

## 1. 共通の仕組み

- [ ] 1.1 `--actor` を必須にする書き込みの基底を足し、--actor が無いときと正の整数でないときに終了コード 2 になることをテストで確かめる
- [ ] 1.2 lib/core の失敗の種類を終了コードに対応させ、4 種類それぞれの error.code と終了コードをテストで確かめる
- [ ] 1.3 CORE_APP_ID の読み込みを足し、無いときと範囲の外のときに user create が終了コード 2 になることをテストで確かめる
- [ ] 1.4 パスワードの入力（--password-stdin と表示しない入力）を足し、標準入力から読めることと、端末が無く --password-stdin も無いときに終了コード 2 になることをテストで確かめる

## 2. user のコマンド

- [ ] 2.1 会員の data の形を作り、12 項目がそろい、パスワードとハッシュが出ないことをテストで確かめる
- [ ] 2.2 user create を足し、登録・重複の CONFLICT・値の誤りの VALIDATION をテストで確かめる
- [ ] 2.3 user get と user list を足し、3 種類の ID での指定・NOT_FOUND・絞り込みと件数と total をテストで確かめる
- [ ] 2.4 user update を足し、片方だけの変更と、何も指定しないときの VALIDATION をテストで確かめる
- [ ] 2.5 user suspend と user activate を足し、遷移と CONFLICT、--reason の記録をテストで確かめる
- [ ] 2.6 user set-password と user authenticate を足し、変えた後の照合の成功と、3 つの失敗の理由が同じ AUTH_FAILED になることをテストで確かめる
- [ ] 2.7 人向けの表示を足し、一覧が 1 人 1 行で全件数が出ることと、時刻が JST で出ることをテストで確かめる

## 3. 文書と動作の確認

- [ ] 3.1 会員の data の形と各コマンドの data を `docs/json-contract.md` に足し、`README.md` のコマンドの表を更新する
- [ ] 3.2 `installDist` した core-cli で、create → list → get → update → suspend → activate → set-password → authenticate を順に実行し、出力と終了コードが仕様どおりであることを確かめる

## Workflow follow-up

- kotlin-core-app の change `add-user` と合わせて archive し、`openspec/specs/` に cli-user-commands が反映されたことを確かめる
