pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 仓库只在这里声明。模块里再写 repositories 直接构建失败，避免依赖来源分叉。
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "android-base"

include(":app")

// 纯 JVM 模块：业务逻辑放这里，测试不需要设备。
include(":core:common")
include(":core:domain")
include(":core:network")
include(":core:data")

// 依赖 Android 运行时的模块。
include(":core:designsystem")
include(":feature:articles")
