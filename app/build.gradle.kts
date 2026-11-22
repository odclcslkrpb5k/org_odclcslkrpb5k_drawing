plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.detekt)
    alias(libs.plugins.licensee)
}

android {
    namespace = "org.odclcslkrpb5k.drawing"
    compileSdk = 37
    buildToolsVersion = "36.1.0"
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

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

// TC-11: every library in the app's runtime classpath has a licence on this list.
licensee { allow("Apache-2.0") }

dependencies {
    // TC-10: floors for Android Lint's own classpath; none of these ships in the APK.
    for (floor in
        listOf(
            libs.floor.guava,
            libs.floor.commons.lang3,
            libs.floor.httpclient,
            libs.floor.bcprov,
            libs.floor.bcpkix,
            libs.floor.bcutil,
        )) {
        constraints.add("androidLintTool", floor)
    }
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.ink.authoring)
    implementation(libs.ink.brush)
    implementation(libs.ink.strokes)
    implementation(libs.ink.rendering)
}
