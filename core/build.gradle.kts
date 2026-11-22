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

// TC-11: every library in this module's runtime classpath has a licence on this list.
licensee { allow("Apache-2.0") }

dependencies {
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.property)
}

tasks.test { useJUnitPlatform() }
