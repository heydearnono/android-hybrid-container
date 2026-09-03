plugins {
    id("base.android.library")
    id("base.android.compose")
}

android {
    namespace = "com.heydearnono.hybrid.feature.web"
}

dependencies {
    // api：WebPageViewModel 实现的是 :core:webview 的 HybridWebViewListener，supertype 在签名里。
    api(project(":core:webview"))
    implementation(project(":core:designsystem"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
}
