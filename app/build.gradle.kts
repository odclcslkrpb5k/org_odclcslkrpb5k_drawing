plugins { alias(libs.plugins.android.application) }

android {
    namespace = "org.odclcslkrpb5k.drawing"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.odclcslkrpb5k.drawing"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkAllWarnings = true
        checkReleaseBuilds = true
        textReport = true
        // API 37 removes the large-screen orientation opt-out the drawing app relies on; see the
        // workspace decisions (2026-11-21). Listed in the suppression check's counted exceptions.
        disable += "OldTargetApi"
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.ink.authoring)
    implementation(libs.ink.brush)
    implementation(libs.ink.strokes)
    implementation(libs.ink.rendering)
}
