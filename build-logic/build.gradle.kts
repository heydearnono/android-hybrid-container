import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "net.xiaoluzhu.crab.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // compileOnly：这三个插件运行期由主工程根 build.gradle.kts 的 apply false 提供
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

// 写成 Plugin<Project> 类而不是预编译脚本插件：类里能读版本目录、能共用 AndroidConfig.kt 那几个函数
gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "crab.jvm.library"
            implementationClass = "net.xiaoluzhu.crab.buildlogic.JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "crab.android.library"
            implementationClass = "net.xiaoluzhu.crab.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "crab.android.application"
            implementationClass = "net.xiaoluzhu.crab.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "crab.android.compose"
            implementationClass = "net.xiaoluzhu.crab.buildlogic.AndroidComposeConventionPlugin"
        }
    }
}
