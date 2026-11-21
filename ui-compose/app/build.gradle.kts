// Resource-bootstrap module only: it links the shared stable resource table
// (processReleaseResources -> full-stable-ids.txt) for the side-panel payload.
// The standalone desktop UI and its payload were removed on 2026-11-20.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

providers.gradleProperty("mdlBuildRoot").orNull?.let { layout.buildDirectory.set(file(it)) }

android {
    namespace = "dev.mindlink.compose"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.mindlink"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    sourceSets {
        getByName("main") {
            val stage = providers.gradleProperty("mdlResourceStage").orNull
            if (stage != null) res.setSrcDirs(listOf(stage))
            else res.srcDir("../generated-res")
            assets.srcDir(layout.buildDirectory.dir("generated/notice-assets"))
            java.srcDir(layout.buildDirectory.dir("generated/platform-sources"))
        }
    }
    androidResources {
        additionalParameters += listOf("--stable-ids", providers.gradleProperty("mdlStableIds")
            .orElse(rootProject.file("stable-ids.txt").absolutePath).get())
    }
}

providers.gradleProperty("mdlEmitIds").orNull?.let { emitted ->
    tasks.configureEach {
        if (name == "processReleaseResources") {
            inputs.property("mindlinkResourceIdsExport", emitted)
            doLast {
                // AGP owns --emit-ids; export its actual linked IDs, not a second argument.
                val linkedIds = layout.buildDirectory.file("intermediates/stable_resource_ids_file/release/processReleaseResources/stableIds.txt").get().asFile
                check(linkedIds.isFile) { "AGP full linked resource IDs are absent" }
                val target = file(emitted)
                target.parentFile.mkdirs()
                linkedIds.copyTo(target, overwrite = true)
            }
        }
    }
}

val prepareNoticeAssets = tasks.register<Copy>("prepareNoticeAssets") {
    from(providers.gradleProperty("mdlNoticeStage").orNull?.let { file(it) }
        ?: rootProject.file("../assets/mindlink"))
    into(layout.buildDirectory.dir("generated/notice-assets/mindlink"))
}
val preparePlatformSources = tasks.register<Copy>("preparePlatformSources") {
    from(rootProject.file("../java_src")) {
        include("wex/ui/core/feature/ModuleDashboardState.java", "wex/ui/core/feature/LogPreview.java")
    }
    into(layout.buildDirectory.dir("generated/platform-sources"))
}
tasks.named("preBuild").configure { dependsOn(prepareNoticeAssets, preparePlatformSources) }

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencyLocking { lockAllConfigurations() }

// DefaultLifecycleObserver is already in lifecycle-common-jvm 2.10.0.
configurations.configureEach {
    exclude(group = "androidx.lifecycle", module = "lifecycle-common-java8")
}

dependencies {
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("io.github.kyant0:shapes:1.2.0")
    implementation("androidx.compose.material3:material3:1.5.0-alpha28")
    implementation("androidx.compose.foundation:foundation:1.13.0-alpha01")
    implementation("androidx.compose.animation:animation:1.13.0-alpha01")
    implementation("androidx.compose.ui:ui:1.13.0-alpha01")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material:material-ripple:1.12.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.10.0")
    implementation("androidx.savedstate:savedstate:1.4.0")
}

tasks.register("writeRuntimeInventory") {
    val runtime = configurations.named("releaseRuntimeClasspath")
    inputs.files(runtime)
    val output = layout.buildDirectory.file("reports/runtime-artifacts.tsv")
    outputs.file(output)
    doLast {
        val lines = runtime.get().resolvedConfiguration.resolvedArtifacts.sortedBy { it.moduleVersion.id.toString() }
            .map { "${it.moduleVersion.id}\t${it.file.absolutePath}" }
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}
