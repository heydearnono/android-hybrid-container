package net.xiaoluzhu.crab.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            configureAndroidCommon()

            val application = extensions.getByType(ApplicationExtension::class.java)
            application.defaultConfig.targetSdk = intVersionOf("targetSdk")
            application.defaultConfig.versionName = versionOf("versionName")
        }
    }
}
