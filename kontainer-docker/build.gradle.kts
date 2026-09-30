plugins {
    kotlin("multiplatform")
    id("io.github.youndie.sborka.kmp")
    id("io.github.youndie.sborka.lint")
    id("io.github.youndie.sborka.publish")
}

kotlin {
    // linuxX64 only in v1 (research D2). The JVM comes later; the code that needs the platform —
    // the socket preflight and the environment — is behind expect/actual so it can follow.
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            // One call reads the unix socket itself (exec output: RawResponse.kt). Already on the classpath
            // through the CIO engine; named because this module uses it directly.
            implementation(libs.ktor.network)
            implementation(wip.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(wip.kotlinx.coroutines.test)
        }
    }
}
