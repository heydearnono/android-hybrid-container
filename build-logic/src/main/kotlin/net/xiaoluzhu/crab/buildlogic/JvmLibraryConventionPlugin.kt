package net.xiaoluzhu.crab.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/** 纯 JVM 模块：判定逻辑落在这里，因为这是唯一能在 JVM 上真测的地方。 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("java-library")
            // AGP 内置的 Kotlin 只对 AGP 模块生效，纯 JVM 模块反过来必须自己 apply KGP
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("com.android.lint")
            configureKotlinJvm()
        }
    }
}
