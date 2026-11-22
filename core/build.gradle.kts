plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
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

dependencies {
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.property)
}

tasks.test { useJUnitPlatform() }
