plugins {
    id("base.android.library")
    id("base.android.compose")
}

android {
    namespace = "com.heydearnono.hybrid.core.webview"
}

dependencies {
    // api：容器的公开签名里有 BridgeDispatcher / BridgeSecurityConfig，调用方必须能看见。
    api(project(":core:bridge"))
    implementation(project(":core:common"))

    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.core)
    // androidContext()：Toaster / KeyValueStore 的实现都要 Context。
    implementation(libs.koin.android)
}
