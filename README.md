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
| 3 | ロジックと View の分離、盤面モデルのユニットテスト | 未着手 |
| 4 | CPU 対戦（思考ルーチン・難易度選択） | 未着手 |
| 5 | 画面回転・状態保存への対応 | 未着手 |

## 画面

- 盤と石は画像ではなく Canvas で描画する。マスの大きさは View の実寸から算出するため、
  画面の密度やサイズに依存しない。
- システムバーのインセットは `MainActivity` が padding として View に渡し、
  `ReversiView` はその padding の内側に盤を配置する。
