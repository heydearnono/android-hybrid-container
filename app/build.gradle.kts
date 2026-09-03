plugins {
    id("base.android.application")
    id("base.android.compose")
}

android {
    namespace = "com.heydearnono.hybrid"

    defaultConfig {
        applicationId = "com.heydearnono.hybrid"
        versionCode = 1
        versionName = "0.1.0"
    }

    // AGP 9 起 buildConfig 默认关闭。这里需要 BuildConfig.DEBUG 来决定是否打开网络日志。
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    // :app 是唯一知道「实现是谁」的地方——只有这里能依赖 :core:data / :core:network。
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":feature:articles"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.android)
}
