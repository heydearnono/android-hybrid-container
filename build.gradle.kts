// 这里只声明插件、不 apply。作用是把 AGP / KGP 放进构建 classpath，
// 让 build-logic 里 compileOnly 编译的 convention plugin 在运行期能 apply 到它们。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
}

// 格式化在根项目统一配置，覆盖所有模块和 build-logic：一处配置、一个命令。
// 规则细节走 .editorconfig，IDE 和 ktlint 读同一份。
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
}
