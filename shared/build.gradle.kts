import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

/**
 * Builds the app's resources folder: the static resources in `src/commonMain/appResources/` (fonts, licenses), the
 * static placeholders from `content/placeholders/`, and `files/shelf.json`, the shelf the app shows until it has been
 * online once: every story in `content/catalog.json`, in order, with its access and its title in its first language and
 * each approved translation. No story ships inside the app: a reader downloads each one in the language they pick
 * (data/HostedStoryLibrary.kt), and the catalog fetched at launch replaces this copy (plan.md §7.3).
 */
abstract class BundleContent : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val staticResources: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val content: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        staticResources.get().asFile.copyRecursively(out)
        val files = File(out, "files")
        val root = content.get().asFile

        @Suppress("UNCHECKED_CAST")
        fun read(file: File) = JsonSlurper().parse(file) as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val entries = read(File(root, "catalog.json"))["stories"] as List<Map<String, Any?>>
        val shelf =
            entries.map { entry ->
                val folder = File(root, "stories/${entry["id"]}")
                val texts =
                    File(folder, "story")
                        .listFiles { f -> f.extension == "json" }
                        .orEmpty()
                        .sortedBy { it.name }
                        .map { read(it) }
                val approved =
                    texts.filter { text ->
                        text["translatedFrom"] == null ||
                            File(folder, "translations/${text["language"]}.json").let { it.isFile && read(it)["status"] == "approved" }
                    }
                // The first language first, as in the published catalog.
                val ordered = approved.sortedBy { if (it["translatedFrom"] == null) 0 else 1 }
                entry.filterKeys { it in listOf("id", "access", "product") } +
                    mapOf(
                        "ageBand" to ordered.firstOrNull()?.get("ageBand"),
                        "title" to ordered.associate { it["language"] to it["title"] },
                    )
            }
        files.mkdirs()
        File(files, "shelf.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(mapOf("format" to 1, "stories" to shelf))))
        File(root, "placeholders")
            .listFiles { f -> f.extension == "svg" }
            .orEmpty()
            .forEach { it.copyTo(File(files, "placeholders/${it.name}")) }
    }
}

/**
 * Writes the story format's shared fixtures into a Kotlin file for commonTest: `schema/fixtures/<name>/story/<lang>.json`,
 * every `content/stories/<id>/story/<lang>.json` and `content/catalog.json`. The app's tests then read the very files
 * the pipeline's tests read (plan.md §10), on the JVM and on iOS alike.
 */
abstract class GenerateFixtures : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemaFixtures: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val stories: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalog: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        fun literal(text: String) =
            buildString {
                append('"')
                for (c in text) {
                    when (c) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '$' -> append("\\$")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(c)
                    }
                }
                append('"')
            }

        // story id -> its files, one per language: <id>/story/<lang>.json
        fun entries(files: Set<File>) =
            files
                .groupBy { it.parentFile.parentFile.name }
                .toSortedMap()
                .entries
                .joinToString("") { (id, languages) ->
                    val texts =
                        languages.sortedBy { it.name }.joinToString("") {
                            "                ${literal(it.nameWithoutExtension)} to ${literal(it.readText())},\n"
                        }
                    "        ${literal(id)} to\n            mapOf(\n$texts            ),\n"
                }

        val fixtures = schemaFixtures.files
        val content = stories.files
        val source =
            """
            |// Generated by the generateFixtures task in shared/build.gradle.kts. Don't edit.
            |package com.nicolasguillen.taletime.fixtures
            |
            |/** `schema/fixtures/<name>/story/<lang>.json`: name → language → the file. */
            |val SCHEMA_FIXTURES: Map<String, Map<String, String>> =
            |    mapOf(
            |${entries(fixtures)}    )
            |
            |/** Every `content/stories/<id>/story/<lang>.json`: story id → language → the file. */
            |val CONTENT_STORIES: Map<String, Map<String, String>> =
            |    mapOf(
            |${entries(content)}    )
            |
            |/** `content/catalog.json`. */
            |val CONTENT_CATALOG: String = ${literal(catalog.get().asFile.readText())}
            |
            """.trimMargin()
        val file = File(outputDir.get().asFile, "com/nicolasguillen/taletime/fixtures/Fixtures.kt")
        file.parentFile.mkdirs()
        file.writeText(source)
    }
}

val bundleContent =
    tasks.register<BundleContent>("bundleContent") {
        staticResources.set(layout.projectDirectory.dir("src/commonMain/appResources"))
        content.set(rootProject.layout.projectDirectory.dir("content"))
        outputDir.set(layout.buildDirectory.dir("bundledContent"))
    }

val generateFixtures =
    tasks.register<GenerateFixtures>("generateFixtures") {
        val root = rootProject.layout.projectDirectory
        schemaFixtures.from(root.dir("schema/fixtures").asFileTree.matching { include("*/story/*.json") })
        stories.from(root.dir("content/stories").asFileTree.matching { include("*/story/*.json") })
        catalog.set(root.file("content/catalog.json"))
        outputDir.set(layout.buildDirectory.dir("generated/fixtures/kotlin"))
    }

kotlin {
    compilerOptions {
        // Room's database constructor is an `expect object`.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.nicolasguillen.taletime.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        androidResources.enable = true
        // Runs commonTest on the JVM too (fast feedback, no simulator needed).
        withHostTest {}
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.viewmodel.navigation)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okio)
            implementation(libs.ktor.client.core)
            implementation(libs.room3.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.datastore.preferences.core)
            implementation(libs.coil.compose)
            implementation(libs.coil.svg)
        }
        commonTest {
            kotlin.srcDir(generateFixtures.flatMap { it.outputDir })
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
                implementation(libs.ktor.client.mock)
                implementation(libs.okio.fakefilesystem)
            }
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

dependencies {
    // Room's code generator runs per target (KSP has no commonMain pass for Room).
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.room3.compiler) }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

compose.resources {
    packageOfResClass = "com.nicolasguillen.taletime.resources"
    publicResClass = false
    customDirectory(sourceSetName = "commonMain", directoryProvider = bundleContent.flatMap { it.outputDir })
}
