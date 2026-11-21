import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

providers.gradleProperty("mdlSidePanelBuildRoot").orNull?.let { layout.buildDirectory.set(file(it)) }

android {
    namespace = "dev.mindlink.sidepanel"
    compileSdk = 37
    defaultConfig {
        // Resource package matches the final installed APK; this UI APK is never installed separately.
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
            // R8 shrank/renamed the Compose runtime into a 2.7k-class payload; the shipped
            // payload keeps the full pinned runtime, so allow a parity build via -PmdlPayloadMinify=false.
            isMinifyEnabled = providers.gradleProperty("mdlPayloadMinify").orNull?.toBoolean() ?: true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    lint {
        // Only an explicitly labelled payload candidate may omit unavailable offline lint tooling.
        // AAR metadata, duplicate classes, Kotlin compilation and final resource checks remain enabled.
        checkReleaseBuilds = !providers.gradleProperty("mdlSkipPayloadLint").map(String::toBoolean).getOrElse(false)
    }
    sourceSets {
        getByName("main") {
            val stage = providers.gradleProperty("mdlSidePanelResourceStage")
                .orElse(providers.gradleProperty("mdlResourceStage")).orNull
            if (stage != null) res.setSrcDirs(listOf(stage))
            else res.srcDir("../generated-res")
        }
    }
    androidResources {
        additionalParameters += listOf("--stable-ids", providers.gradleProperty("mdlSidePanelStableIds")
            .orElse(rootProject.file("../stable-ids.txt").absolutePath).get())
    }
}

providers.gradleProperty("mdlSidePanelEmitIds").orNull?.let { emitted ->
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

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencyLocking { lockAllConfigurations() }

// The obsolete java8 forwarding artifact has no classes (verified from cached 2.11.0).
// DefaultLifecycleObserver is already in lifecycle-common-jvm 2.10.0; do not fetch a redundant JAR.
configurations.configureEach {
    exclude(group = "androidx.lifecycle", module = "lifecycle-common-java8")
}

// Only the locally cached WeKit Material3 runtime enters this payload. The desktop Material3
// graph stays in :app and uses a DIFFERENT boot-parent InMemoryDexClassLoader, never an ABI mixture.
dependencies {
    // Byte-identical selected vector classes from cached composeicons 2.2.1; per-class hashes in provenance.
    implementation(files("thirdparty/material-symbols-selected-2.2.1.jar"))
    implementation("androidx.compose.material3:material3:1.5.0-alpha28")
    implementation("androidx.compose.foundation:foundation:1.13.0-alpha01")
    implementation("androidx.compose.animation:animation:1.13.0-alpha01")
    implementation("androidx.compose.ui:ui:1.13.0-alpha01")
    // Cached compatible releases satisfy Material3's older minimums without any network fetch.
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material:material-ripple:1.12.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.10.0")
    implementation("androidx.savedstate:savedstate:1.4.0")
}

val verifyVendoredSources = tasks.register("verifyVendoredSources") {
    val proof = rootProject.file("thirdparty/sourceprovenance/wekit-sidepanel.json")
    val iconJar = rootProject.file("thirdparty/material-symbols-selected-2.2.1.jar")
    inputs.file(proof)
    inputs.file(iconJar)
    inputs.files(fileTree("src/main/java/dev/mindlink/sidepanel/vendor/wekit") { include("*.kt") })
    doLast {
        @Suppress("UNCHECKED_CAST")
        val record = groovy.json.JsonSlurper().parse(proof) as Map<String, Any>
        fun sha256(file: java.io.File): String = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val icons = record["icons"] as Map<*, *>
        check(sha256(iconJar) == icons["sha256"]) { "Original selected Material Symbols checksum changed" }
        val sources = record["copiedAndModified"] as List<*>
        for (source in sources) {
            val entry = source as Map<*, *>
            val target = entry["target"] as? String ?: continue
            check(sha256(rootProject.file(target)) == entry["targetSha256"]) {
                "WeKit copied source differs from its recorded diff: $target"
            }
        }
        logger.lifecycle("PASS: WeKit vendored component and selected original icon hashes")
    }
}
tasks.named("preBuild").configure { dependsOn(verifyVendoredSources) }

tasks.register("writeRuntimeInventory") {
    val runtime = configurations.named("releaseRuntimeClasspath")
    inputs.files(runtime)
    val output = layout.buildDirectory.file("reports/runtime-artifacts.tsv")
    outputs.file(output)
    doLast {
        val lines = runtime.get().resolvedConfiguration.resolvedArtifacts.sortedBy { it.moduleVersion.id.toString() }
            .map { "${it.moduleVersion.id}\t${it.file.absolutePath}" }.distinct()
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}
