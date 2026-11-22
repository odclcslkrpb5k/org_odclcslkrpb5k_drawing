import javax.inject.Inject
import org.gradle.process.ExecOperations

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
        // LN-6: a result left unused is an error here too (an ignored Outcome hides a failure).
        freeCompilerArgs.add("-Xreturn-value-checker=full")
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

// The coloring pages: templates/*.svg rendered, labelled and checked by tools/templates at build
// time, so the app only looks regions up (see tools/templates). resvg is the image's, named by
// $RESVG; outside the image the build stops at this task's missing input.
val templateTool = configurations.create("templateTool") { isCanBeConsumed = false }

dependencies { templateTool(project(":templates")) }

abstract class MakeTemplates @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pages: DirectoryProperty

    @get:Classpath abstract val tool: ConfigurableFileCollection

    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val resvg: RegularFileProperty

    @get:OutputDirectory abstract val out: DirectoryProperty

    @get:Internal abstract val work: DirectoryProperty

    @TaskAction
    fun make() {
        val assets = out.get().dir("templates").asFile
        assets.deleteRecursively()
        exec
            .javaexec {
                classpath = tool
                mainClass = "org.odclcslkrpb5k.drawing.templates.adapters.MainKt"
                args(pages.get().asFile, assets, work.get().asFile, resvg.get().asFile)
            }
            .assertNormalExitValue()
    }
}

val makeTemplates =
    tasks.register<MakeTemplates>("makeTemplates") {
        pages = rootProject.layout.projectDirectory.dir("templates")
        tool.from(templateTool)
        resvg.fileProvider(providers.environmentVariable("RESVG").map { File(it) })
        out = layout.buildDirectory.dir("generated/templateAssets")
        work = layout.buildDirectory.dir("tmp/templates")
    }

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(makeTemplates, MakeTemplates::out)
    }
}
