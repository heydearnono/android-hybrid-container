package com.heydearnono.hybrid.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.getByType

/** 版本目录入口。所有版本号只允许来自 `gradle/libs.versions.toml`，不在 convention plugin 里硬编码。 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.versionOf(name: String): String = findVersion(name).get().requiredVersion

internal fun VersionCatalog.intVersionOf(name: String): Int = versionOf(name).toInt()

internal fun DependencyHandler.impl(
    catalog: VersionCatalog,
    alias: String,
) {
    add("implementation", catalog.findLibrary(alias).get())
}

internal fun DependencyHandler.testImpl(
    catalog: VersionCatalog,
    alias: String,
) {
    add("testImplementation", catalog.findLibrary(alias).get())
}

/**
 * 全仓统一的测试依赖：JUnit 4 + kotlin.test + 协程测试 + Turbine。
 *
 * 刻意不引 mock 框架。接口变了 fake 会编译不过、错误显式；mock 只在运行期炸，更难判断。
 */
internal fun DependencyHandler.commonTestDependencies(catalog: VersionCatalog) {
    testImpl(catalog, "junit4")
    testImpl(catalog, "kotlin-test")
    testImpl(catalog, "kotlinx-coroutines-test")
    testImpl(catalog, "turbine")
}
