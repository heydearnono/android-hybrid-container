plugins {
    id("crab.android.library")
}

android {
    namespace = "net.xiaoluzhu.crab.webview"
}

dependencies {
    // 必须是 api：公开类的超类型来自 implementation 依赖时，:app 会报
    // Cannot access '...' which is a supertype of '...'
    api(project(":core:container"))
    implementation(libs.androidx.webkit)
}
