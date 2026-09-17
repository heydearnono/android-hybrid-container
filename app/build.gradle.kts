plugins {
    id("crab.android.application")
    id("crab.android.compose")
}

android {
    namespace = "net.xiaoluzhu.crab"

    defaultConfig {
        // 标识取 pro 的取值表：三端同一个字符串。applicationId 与 namespace 是两个东西，这里刻意取同值
        applicationId = "net.xiaoluzhu.crab"
        versionCode = 1
    }

    buildFeatures {
        // AGP 9 默认 false；调试开关要按构建类型取值，得读 BuildConfig.DEBUG
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:webview"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
