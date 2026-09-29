pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        // Written out by hand, and it has to be: `pluginManagement` is evaluated before any settings
        // plugin is applied, including the sborka one, which is fetched through it.
        maven("https://reposilite.kotlin.website/snapshots") {
            name = "wip-snapshots"
            content { includeGroupByRegex("io\\.github\\.youndie.*") }
        }
    }
}

plugins {
    // The repositories with their content filters, the shared `wip` catalog (which carries the
    // compiler version), and the check that `.editorconfig` is the portfolio's.
    id("io.github.youndie.sborka.settings") version "0.4.0.93"
}

rootProject.name = "kontainer"

// The Docker Engine client, usable without the fixture layer (docs/services/kontainer-docker.md).
include(":kontainer-docker")

// Fixtures from compose files, ports, readiness, faults (docs: the drafted kontainer module document).
include(":kontainer")
