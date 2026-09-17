package net.xiaoluzhu.crab.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // 不 apply org.jetbrains.kotlin.android：AGP 9 内置 Kotlin 且默认开启，再 apply KGP 会冲突
            pluginManager.apply("com.android.library")
            configureAndroidCommon()
        }
    }
}
