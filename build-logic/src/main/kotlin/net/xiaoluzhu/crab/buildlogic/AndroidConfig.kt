package net.xiaoluzhu.crab.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// AGP 9 的 CommonExtension 没有泛型参数，且 defaultConfig / lint / compileOptions / testOptions
// 只有 val、没有接 lambda 的重载，所以下面一律用属性赋值。唯一是函数的是 compileSdk(action)。
internal fun Project.androidExtension(): CommonExtension = extensions.getByType(CommonExtension::class.java)

internal fun Project.configureAndroidCommon() {
    val android = androidExtension()

    // 旧的 compileSdkVersion(...) 已标记 AGP 10 移除；本机只装了 android-37.0，所以必须带 minorApiLevel
    android.compileSdk {
        version =
            release(intVersionOf("compileSdk")) {
                minorApiLevel = intVersionOf("compileSdkMinor")
            }
    }
    android.defaultConfig.minSdk = intVersionOf("minSdk")
    android.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    android.compileOptions.targetCompatibility = JavaVersion.VERSION_17

    // 刻意不开 testOptions.unitTests.isReturnDefaultValues。开了以后 android.jar 的 stub 会静默返回
    // 默认值，给 WebView 那一侧写的测试会绿、而那个绿是假的（pro 的 M3 明写这比没有测试更坏）。
    // 保持 AGP 默认的 false：单测里碰到框架对象当场抛「not mocked」，那正是我们要的信号。

    configureLint(android.lint)

    // 内置 Kotlin 注册的仍是 KGP 的 KotlinAndroidProjectExtension（实测），所以这里能配 jvmTarget
    extensions
        .getByType(KotlinAndroidProjectExtension::class.java)
        .compilerOptions.jvmTarget
        .set(JvmTarget.JVM_17)

    addCommonTestDependencies()
}

internal fun Project.configureKotlinJvm() {
    val java = extensions.getByType(JavaPluginExtension::class.java)
    java.sourceCompatibility = JavaVersion.VERSION_17
    java.targetCompatibility = JavaVersion.VERSION_17

    extensions
        .getByType(KotlinJvmProjectExtension::class.java)
        .compilerOptions.jvmTarget
        .set(JvmTarget.JVM_17)
    // 纯 JVM 模块额外 apply 了 com.android.lint，不加的话 :app:lint 会把它当外部依赖跳过，
    // 等于判定逻辑那一整块没被检查
    configureLint(extensions.getByType(Lint::class.java))

    addCommonTestDependencies()
}

private fun configureLint(lint: Lint) {
    lint.abortOnError = true
    lint.checkDependencies = true
    lint.warningsAsErrors = false
    // 这三条的结果取决于「今天 Maven 上有什么」，会让同一份代码今天绿明天黄；升级依赖是显式决定
    lint.disable.addAll(listOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable"))
}
