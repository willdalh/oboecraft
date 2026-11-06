// The Fabric adapter: everything that touches Minecraft and Fabric.
plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = "oboecraft"
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("oboecraft") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
            // core and json are shipped inside the mod jar, so in dev they belong to the same mod.
            sourceSet("main", ":core")
            sourceSet("main", ":json")
        }
    }
}

fabricApi {
    // Adds the gametest source set and runGameTest, which check (and so build) runs.
    configureTests {
        createSourceSet = true
        modId = "oboecraft-gametest"
        enableGameTests = true
        enableClientGameTests = true
    }
}

// Write a JUnit report of the game tests, next to the other test results.
tasks.named<JavaExec>("runGameTest") {
    val report = layout.buildDirectory.file("test-results/gameTest/TEST-gametest.xml")
    systemProperty("fabric-api.gametest.report-file", report.get().asFile.absolutePath)
}

// Jars bundled into the mod jar.
interface ArchiveServices {
    @get:Inject
    val archives: ArchiveOperations
}

val bundled = configurations.create("bundled") {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)

    implementation(project(":core"))
    bundled(project(":core"))
    implementation(project(":json"))
    bundled(project(":json"))
}

tasks.withType<ProcessResources>().configureEach {
    val properties = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to libs.versions.minecraft.get(),
        "loader_version" to libs.versions.fabric.loader.get(),
    )
    inputs.properties(properties)

    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}

tasks.jar {
    // Ship the classes of core and json inside the mod jar, so the mod is one plain jar.
    val archives = objects.newInstance<ArchiveServices>().archives
    from(bundled.elements.map { jars -> jars.map { archives.zipTree(it.asFile) } }) {
        exclude("META-INF/MANIFEST.MF")
    }
    from(rootProject.file("LICENSE")) {
        rename { "LICENSE_oboecraft" }
    }
    // The credits for the Japanese names and readings in the name list.
    from(rootProject.file("CREDITS.md")) {
        rename { "CREDITS_oboecraft.md" }
    }
}
