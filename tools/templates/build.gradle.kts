// The coloring-page builder: runs at build time only, never ships in the app.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.licensee)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
        progressiveMode = true
        freeCompilerArgs.add("-Xreturn-value-checker=full")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

licensee { allow("Apache-2.0") }

dependencies {
    implementation(project(":core"))
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.property)
}

tasks.test { useJUnitPlatform() }
