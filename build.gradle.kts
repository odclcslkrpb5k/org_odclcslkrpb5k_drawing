// TC-3: the plugins' own classpath is locked as well (buildscript-gradle.lockfile).
buildscript { dependencyLocking { lockAllConfigurations() } }

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.detekt) apply false
}

allprojects {
    // TC-3: every resolved configuration is locked; a changed resolution fails until relocked.
    dependencyLocking { lockAllConfigurations() }
    // One top-level build directory: the ship gate matches ignored paths by top-level name only.
    layout.buildDirectory = rootProject.layout.projectDirectory.dir("build/${project.name}")
}

// Resolves every resolvable configuration, so the image build can fetch everything the offline
// check will need (TS-1). Used only by tools/checks/warm.
tasks.register("resolveAll") {
    val roots = allprojects.map { p ->
        p.configurations
            .filter { it.isCanBeResolved }
            .map { it.incoming.artifactView { lenient(true) }.files }
    }
    doLast { roots.flatten().forEach { it.files } }
}

// TC-6: one formatter, run from its own pinned artifact rather than a third-party plugin (TC-7).
val ktfmt =
    configurations.create("ktfmt") {
        attributes { attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED)) }
    }

dependencies { ktfmt(libs.ktfmt) }

fun kotlinSources() =
    fileTree(rootDir) {
        include("**/*.kt", "**/*.kts")
        exclude("build/**", ".gradle/**", "vendor/**", "test-fixtures/**")
    }

tasks.register<JavaExec>("ktfmtCheck") {
    classpath = ktfmt
    mainClass = "com.facebook.ktfmt.cli.Main"
    args("--kotlinlang-style", "--dry-run", "--set-exit-if-changed")
    argumentProviders.add(
        CommandLineArgumentProvider { kotlinSources().files.map { it.path }.sorted() }
    )
}

tasks.register<JavaExec>("ktfmtFormat") {
    classpath = ktfmt
    mainClass = "com.facebook.ktfmt.cli.Main"
    args("--kotlinlang-style")
    argumentProviders.add(
        CommandLineArgumentProvider { kotlinSources().files.map { it.path }.sorted() }
    )
}
