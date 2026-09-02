plugins {
    id("base.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:common"))

    api(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    api(libs.kotlinx.serialization.json)
    implementation(libs.koin.core)

    // MockWebServer 是纯 JVM 的，能真实覆盖序列化和状态码分支，不需要设备。
    testImplementation(libs.okhttp.mockwebserver)
}
