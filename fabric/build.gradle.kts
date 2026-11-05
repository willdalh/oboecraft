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
        }
    }
}

fabricApi {
    // Adds the gametest source set and runGameTest, which check (and so build) runs.
    configureTests {
        createSourceSet = true
        modId = "oboecraft-gametest"
        enableGameTests = true
        enableClientGameTests = false
    }
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)

    implementation(project(":core"))
}

tasks.withType<ProcessResources>().configureEach {
    val version = project.version
    inputs.property("version", version)

    filesMatching("fabric.mod.json") {
        expand("version" to version)
    }
}

tasks.jar {
    // Ship core's classes inside the mod jar, so the mod is one plain jar.
    from(project(":core").sourceSets.main.map { it.output })
    from(rootProject.file("LICENSE")) {
        rename { "LICENSE_oboecraft" }
    }
}
