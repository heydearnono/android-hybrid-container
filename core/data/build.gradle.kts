plugins {
    id("base.jvm.library")
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(libs.koin.core)
}
