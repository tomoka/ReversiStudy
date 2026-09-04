// トップレベルのビルド設定。プラグインのバージョンをここで一元管理する。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
