plugins {
    kotlin("multiplatform")
    id("io.github.youndie.sborka.kmp")
    id("io.github.youndie.sborka.lint")
}

kotlin {
    // linuxX64 only in v1 (research D2). Running the compose CLI is behind expect/actual.
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            // By path: type-safe project accessors are off, because a module named like the root project
            // (`:kontainer` in `kontainer`) makes Gradle generate `getKontainer()` twice.
            api(project(":kontainer-docker"))
            implementation(wip.kotlinx.serialization.json)
            // Probes: raw TCP for the Kafka and Postgres protocols, the HTTP client for `http`.
            implementation(libs.ktor.network)
            implementation(libs.ktor.client.cio)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(wip.kotlinx.coroutines.test)
        }
    }
}
