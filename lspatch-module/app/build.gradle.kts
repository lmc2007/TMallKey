plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fktm.tmxydump"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fktm.tmxydump"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // 模块签名不重要（LSPatch 不校验），签名只为让 APK 能被正常安装/解析
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Xposed API 只用于编译，运行时由 LSPatch 提供
    compileOnly(files("libs/xposed-api-82.jar"))
    compileOnly("androidx.annotation:annotation:1.9.1")
}
