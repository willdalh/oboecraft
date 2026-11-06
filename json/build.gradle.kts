// File formats in JSON that the mod and its tools share. Plain Java and Gson, with no Minecraft or Fabric.
plugins {
    `java-library`
    // Only exports the classpath, so Loom can put this module in the oboecraft mod when running in dev, like core.
    id("net.fabricmc.fabric-loom-companion")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":core"))
    // Minecraft ships Gson, so the mod uses the game's copy. The version matches the one in Minecraft.
    compileOnly(libs.gson)

    testImplementation(libs.gson)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
