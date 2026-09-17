package net.xiaoluzhu.crab.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // AGP 内置的 Kotlin 不带 Compose 编译器插件，buildFeatures.compose = true 时必须额外 apply 它，
            // 且版本必须等于 AGP 内置的 Kotlin 版本；不加会在 configuration 阶段就报缺插件
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val android = androidExtension()
            android.buildFeatures.compose = true

            val bom = dependencies.platform(library("androidx-compose-bom"))
            dependencies.impl(bom)
            dependencies.impl(library("androidx-compose-ui"))
            dependencies.impl(library("androidx-compose-ui-graphics"))
            dependencies.impl(library("androidx-compose-ui-tooling-preview"))
            dependencies.impl(library("androidx-compose-foundation"))
            dependencies.impl(library("androidx-compose-material3"))
            dependencies.add("debugImplementation", bom)
            dependencies.add("debugImplementation", library("androidx-compose-ui-tooling"))
        }
    }
}
