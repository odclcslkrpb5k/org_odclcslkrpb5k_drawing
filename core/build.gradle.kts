plugins { alias(libs.plugins.kotlin.jvm) }

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
        progressiveMode = true
    }
}

dependencies {
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.property)
}

tasks.test { useJUnitPlatform() }
