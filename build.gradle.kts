plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    // One top-level build directory: the ship gate matches ignored paths by top-level name only.
    layout.buildDirectory = rootProject.layout.projectDirectory.dir("build/${project.name}")
}

// Resolves every resolvable configuration, so the image build can fetch everything the offline
// check will need (TS-1). Used only by tools/checks/warm.
tasks.register("resolveAll") {
    val roots = allprojects.map { p ->
        p.configurations.filter { it.isCanBeResolved }.map { it.incoming.artifactView { lenient(true) }.files }
    }
    doLast { roots.flatten().forEach { it.files } }
}
