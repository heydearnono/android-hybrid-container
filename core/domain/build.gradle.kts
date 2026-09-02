plugins {
    id("base.jvm.library")
}

dependencies {
    // api：domain 的类型签名里会出现 Outcome / AppError，上层必须能看见。
    api(project(":core:common"))
}
