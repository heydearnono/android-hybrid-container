plugins {
    id("base.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // api：BridgeHandler 的签名里有 Outcome / AppError，实现方必须能看见。
    api(project(":core:common"))
    // api：JsonObject / JsonElement 也出现在 BridgeHandler 的签名里。
    api(libs.kotlinx.serialization.json)
    implementation(libs.koin.core)
}
