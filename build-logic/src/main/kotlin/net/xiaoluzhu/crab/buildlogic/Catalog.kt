package net.xiaoluzhu.crab.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider

/** 版本目录是版本号的唯一真相，convention plugin 里的档位与坐标一律从这里读。 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

internal fun Project.versionOf(alias: String): String =
    libs
        .findVersion(alias)
        .orElseThrow { IllegalStateException("版本目录里找不到 version「$alias」") }
        .requiredVersion

internal fun Project.intVersionOf(alias: String): Int = versionOf(alias).toInt()

internal fun Project.library(alias: String): Provider<MinimalExternalModuleDependency> =
    libs
        .findLibrary(alias)
        .orElseThrow { IllegalStateException("版本目录里找不到 library「$alias」") }

internal fun DependencyHandler.impl(dependency: Any) {
    add("implementation", dependency)
}

internal fun DependencyHandler.api(dependency: Any) {
    add("api", dependency)
}

internal fun DependencyHandler.testImpl(dependency: Any) {
    add("testImplementation", dependency)
}

/**
 * 全仓统一 JUnit 4 + kotlin.test，模块里不用重复声明。
 * 不引 MockK 一类 mock 框架：手写 fake 在接口变了的时候编译不过，mock 只在运行期炸。
 */
internal fun Project.addCommonTestDependencies() {
    dependencies.testImpl(library("junit4"))
    dependencies.testImpl(library("kotlin-test"))
}
