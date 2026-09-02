package com.example.base.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

private val JAVA_VERSION = JavaVersion.VERSION_17
private val KOTLIN_JVM_TARGET = JvmTarget.JVM_17

/**
 * 取当前模块的 Android 扩展。AGP 9 的 `CommonExtension` 没有泛型参数，但它不是注册类型，
 * 所以只能按具体类型找再上抛到公共父接口。
 */
internal fun Project.androidExtension(): CommonExtension =
    extensions.findByType(ApplicationExtension::class.java)
        ?: extensions.findByType(LibraryExtension::class.java)
        ?: error("模块 $path 没有 apply com.android.application / com.android.library")

/** Android 模块（app 与 library）共用的配置。 */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    // AGP 9 的 compileSdk block。本机只装了 android-37.0（没有 android-37），minorApiLevel 必须显式给。
    extension.compileSdk {
        this.version =
            release(libs.intVersionOf("compileSdk")) {
                minorApiLevel = libs.intVersionOf("compileSdkMinor")
            }
    }
    extension.defaultConfig.minSdk = libs.intVersionOf("minSdk")

    extension.compileOptions.sourceCompatibility = JAVA_VERSION
    extension.compileOptions.targetCompatibility = JAVA_VERSION

    // 没有真机可跑 instrumented 测试，单测是唯一的自动化验证手段，所以别让 android.jar 的
    // stub 方法抛 "not mocked"——返回默认值，测试跑得起来。
    extension.testOptions.unitTests.isReturnDefaultValues = true

    extension.lint.abortOnError = true
    extension.lint.checkDependencies = true
    extension.lint.warningsAsErrors = false
    // 这三条检查的结果取决于「今天 Maven 上有什么」，同一份代码今天绿明天黄。
    // 升级依赖是一次显式决定（改版本目录 + 重跑 check），不该由 lint 当噪音报出来。
    extension.lint.disable.addAll(
        listOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable"),
    )

    // AGP 9 的内置 Kotlin 注册的仍然是 KGP 的 KotlinAndroidProjectExtension（已实测）。
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(KOTLIN_JVM_TARGET)
        }
    }

    dependencies {
        commonTestDependencies(libs)
    }
}

/** 纯 JVM 模块的配置。业务逻辑尽量待在这里——这些模块的测试不需要设备。 */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget.set(KOTLIN_JVM_TARGET)
        }
    }
    // com.android.lint（独立 lint 插件）注册的扩展，和 Android 模块里的是同一个类型。
    extensions.configure<Lint> {
        abortOnError = true
        warningsAsErrors = false
        disable.addAll(
            listOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable"),
        )
    }
    dependencies {
        impl(libs, "kotlinx-coroutines-core")
        commonTestDependencies(libs)
    }
}
