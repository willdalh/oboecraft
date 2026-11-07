// Tools for building the mod's data, run by hand. Not part of the mod jar.
plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":json"))
    implementation(libs.sudachi)
    implementation(libs.gson)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// Writes the name list from Minecraft's Japanese names, and a list of the generated entries to review.
tasks.register<JavaExec>("generateNameList") {
    group = "oboecraft"
    description = "Writes the name list from Minecraft's Japanese names, and a list of the new entries to review."
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.willdalh.oboecraft.tools.namelist.NameListTool"
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    args(
        "--minecraft-version", libs.versions.minecraft.get(),
        "--name-list", rootProject.file("fabric/src/main/resources/oboecraft/name_list.json").absolutePath,
        "--review-list", layout.buildDirectory.file("name-list/review.md").get().asFile.absolutePath,
        "--cache", layout.buildDirectory.dir("name-list/cache").get().asFile.absolutePath,
    )
}

// Makes a reading clip for each name list entry with a running VOICEVOX engine, and a list of the new clips to review.
// The engine's address can be changed with -Pvoicevox=http://host:port.
tasks.register<JavaExec>("generateReadingClips") {
    group = "oboecraft"
    description = "Makes the reading clips with VOICEVOX, for the entries whose name or reading changed."
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.willdalh.oboecraft.tools.readingclips.ReadingClipTool"
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    args(
        "--name-list", rootProject.file("fabric/src/main/resources/oboecraft/name_list.json").absolutePath,
        "--assets", rootProject.file("fabric/src/client/resources/assets/oboecraft").absolutePath,
        "--clip-list", project.file("reading-clips.json").absolutePath,
        "--review-list", layout.buildDirectory.file("reading-clips/review.md").get().asFile.absolutePath,
        "--engine", providers.gradleProperty("voicevox").getOrElse("http://localhost:50021"),
    )
}
