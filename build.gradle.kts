plugins {
    base
    alias(libs.plugins.sborkaLint)
    // Versions named once, here; modules ask for these by id alone.
    alias(wip.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.sborkaKmp) apply false
}
