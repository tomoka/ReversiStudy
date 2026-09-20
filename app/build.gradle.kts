import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

/**
 * リリース署名の設定は keystore.properties（リポジトリには入れない）か
 * 環境変数から読む。未設定ならリリースビルドは署名されないだけで、
 * デバッグビルドには影響しない。
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun releaseSecret(name: String): String? =
    keystoreProperties.getProperty(name) ?: System.getenv(name)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "mobi.tomo.reversi"
    compileSdk = 36

    defaultConfig {
        // 旧 ID（mobi.tomo.reversi）は Google Play 側で予約されたままのため使えない
        applicationId = "io.github.tomoka.reversi"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSecret("RELEASE_STORE_FILE") != null) {
            create("release") {
                storeFile = file(releaseSecret("RELEASE_STORE_FILE")!!)
                storePassword = releaseSecret("RELEASE_STORE_PASSWORD")
                keyAlias = releaseSecret("RELEASE_KEY_ALIAS")
                keyPassword = releaseSecret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.material)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
