# ReversiStudy

Android 向けのリバーシ。2013 年に Eclipse ADT で作ったものを、Gradle + Kotlin に作り直した。
CPU 対戦ができる。広告なし、通信なし。

- 配布ページ: https://tomoka.github.io/ReversiStudy/
- ダウンロード: [Releases](https://github.com/tomoka/ReversiStudy/releases)

なお、このリポジトリに置いていた 2013 年のコードは、公開用に一部の機能を外した
状態のもので、当時リリースした版そのものではない。CPU 対戦が無く、タイトル画面も
描画されない状態だった。

## ビルド

Android Studio で開くか、コマンドラインから:

```
./gradlew assembleDebug
```

- JDK 17 以上
- compileSdk / targetSdk 36、minSdk 24
- applicationId は `io.github.tomoka.reversi`。旧 ID（`mobi.tomo.reversi`）は
  Google Play 側で予約されたままのため使えない

## リリースビルド

署名鍵を作る（初回のみ）。

```
keytool -genkeypair -v \
  -keystore reversi-release.jks \
  -alias reversi \
  -keyalg RSA -keysize 2048 -validity 10000
```

**鍵とパスワードはリポジトリに入れない。** 鍵の場所とパスワードは、
リポジトリ直下の `keystore.properties`（`.gitignore` 済み）に書く。

```
RELEASE_STORE_FILE=/path/to/reversi-release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=reversi
RELEASE_KEY_PASSWORD=...
```

同じ名前の環境変数でも読む。設定が無ければ署名されないだけで、デバッグ
ビルドには影響しない。

```
./gradlew assembleRelease
```

APK は `app/build/outputs/apk/release/` にできる。

**鍵は無くさないこと。** 同じ鍵で署名しないと、既存の利用者はアプリを
更新できない。

## 配布

Google Play では配信していない。APK を [Releases](https://github.com/tomoka/ReversiStudy/releases)
に置いて配る。

配布ページ（プライバシーポリシー・利用規約を含む）は `docs/` にあり、
GitHub Pages で公開している。有効化はリポジトリの
Settings → Pages → Source を「Deploy from a branch」、Branch を
`master` / `/docs` に設定する。

## 改修フェーズ

| フェーズ | 内容 | 状態 |
| --- | --- | --- |
| 1 | Gradle 化・Kotlin 移植・テンプレート残骸の削除 | 完了 |
| 2 | 盤面描画とタッチ判定を画面サイズ基準に直す、各画面の描画、エッジツーエッジ対応 | 完了 |
| 3 | ロジックと View の分離、盤面モデルのユニットテスト | 完了 |
| 4 | CPU 対戦（αβ探索・終盤読み切り） | 完了 |
| 5 | 画面回転・状態保存への対応 | 完了 |
| 6 | 裏返るアニメーション | 完了 |
| 7 | 立体感（陰影・厚み・影） | 完了 |

## 構成

```
mobi.tomo.reversi
├── MainActivity        画面の生成とインセットの受け渡し
├── ReversiView         描画とタッチ判定のみ
└── game
    ├── Disc            石の色
    ├── Board           盤面。不変。着手すると新しい盤面を返す
    ├── Game            手番の交代、パス、終局の判定
    └── ComputerPlayer  CPU の思考（αβ探索）
```

`game` パッケージは Android に依存しないので、ユニットテストからそのまま使える。

- 盤と石は画像ではなく Canvas で描画する。マスの大きさは View の実寸から算出するため、
  画面の密度やサイズに依存しない。
- システムバーのインセットは `MainActivity` が padding として View に渡し、
  `ReversiView` はその padding の内側に盤を配置する。

## CPU

- αβ探索。評価はマスの重み付け（隅を高く、隅の隣を低く）と着手可能数の差。
- 空きマスが 8 以下になったら最後まで読み切り、石差を最大化する。
- 強さは `ComputerPlayer` の `depth` と `endgameEmpties` で決まる。既定は
  `MEDIUM_DEPTH = 4` で「中くらい」。難易度を選べるようにする場合は、
  この 2 つを切り替えれば足りる。

## 見せ方

2D の Canvas だけで立体的に見せている。素材画像は使っていない。

- 光源は左上ひとつ。石の表面は放射グラデーションで、ハイライトを光源側に寄せる。
- 石には厚みがあり、裏返って真横を向いたときに側面が見える。
- 石は盤に影を落とす。裏返るときは影も一緒に細くなり、浮くと薄くなる。
- 裏返る途中でわずかに持ち上がる。
- 格子は溝の線とその右下の明るい線の 2 本 1 組で、彫り込んだように見せる。
- 盤の四隅をわずかに暗くして奥行きを出す。

石の陰影はコードで作っているので、画像素材に差し替えるときはこの層を
置き換えるだけで済む。

## アニメーション

- 着手すると、裏返る石が横幅を縮めながら色を変える。置いたマスから遠い石ほど
  遅れて返るので、波が広がるように見える。
- 石 1 枚は 240ms、1 マス離れるごとに 30ms 遅れる。最長でも 390ms で、
  CPU が動き出す 600ms より前に終わる。
- 端末の「アニメーションを無効」設定（`ANIMATOR_DURATION_SCALE`）を尊重する。
  0 なら即座に最終状態を描く。
- 裏返っている間はパスや結果の表示を重ねない。

## 画面回転

- 向きの固定を外したので、縦横どちらでも遊べる。盤は使える領域の短い方に合わせて
  正方形に収まる。
- 回転すると Activity は作り直されるため、`ReversiView.onSaveInstanceState` で
  盤面・手番・選んだ色を保存して復元する。盤面は `Board.toDiagram()` の文字列として
  持たせている。
- 復元後は `scheduleNext()` を呼び直すので、CPU の手番やパスの途中で回転しても
  続きが動く。

## テスト

```
./gradlew test
```

`Board` と `Game` のユニットテストが `app/src/test` にある。

## 謝辞

最初の実装（2013年）は、下記の記事とコードで勉強させていただきました。

- M.I.のプログラミング・メモ「Androidでオセロゲームを作ってみる」
  https://blog.makotoishida.com/2011/06/android-1.html
- mikehibm/MiReversi — MIT License, Copyright (c) 2011 Makoto Ishida
  https://github.com/mikehibm/MiReversi

実装そのものは自分で書いたもので、コードの流用はしていません。

2026年の作り直し（Gradle 化、Kotlin 移植、CPU 対戦、アニメーション、
立体表現）は、クロちゃん（Claude）と一緒に進めました。

## ライセンス

MIT License. 詳細は [LICENSE](LICENSE) を参照。

`Copyright (c) 2026 tomotake`
