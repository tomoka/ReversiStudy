# ReversiStudy

Android 向けのリバーシ。2013 年に Eclipse ADT で作ったものを、Gradle + Kotlin に移行中。

## ビルド

Android Studio で開くか、コマンドラインから:

```
./gradlew assembleDebug
```

- JDK 17 以上
- compileSdk / targetSdk 36、minSdk 24

## 改修フェーズ

| フェーズ | 内容 | 状態 |
| --- | --- | --- |
| 1 | Gradle 化・Kotlin 移植・テンプレート残骸の削除 | 完了 |
| 2 | 盤面描画とタッチ判定を画面サイズ基準に直す、各画面の描画、エッジツーエッジ対応 | 完了 |
| 3 | ロジックと View の分離、盤面モデルのユニットテスト | 完了 |
| 4 | CPU 対戦（αβ探索・終盤読み切り） | 完了 |
| 5 | 画面回転・状態保存への対応 | 未着手 |

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

## テスト

```
./gradlew test
```

`Board` と `Game` のユニットテストが `app/src/test` にある。
