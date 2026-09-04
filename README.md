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
| 2 | 盤面描画とタッチ判定を画面サイズ基準に直す（48px 決め打ちの解消） | 未着手 |
| 3 | ロジックと View の分離、盤面モデルのユニットテスト | 未着手 |
| 4 | CPU 対戦（思考ルーチン・難易度選択） | 未着手 |
| 5 | タイトル／パス／結果画面の作り直し、画面回転対応 | 未着手 |
