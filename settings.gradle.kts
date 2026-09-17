pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 仓库只在这里声明一处；模块里再写 repositories 直接构建失败
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "android-hybrid-container"

// 三个模块按「能否在 JVM 上测」切：判定逻辑全在 :core:container，碰 android.webkit 的直线代码全在 :core:webview
include(":app")
include(":core:container")
include(":core:webview")
