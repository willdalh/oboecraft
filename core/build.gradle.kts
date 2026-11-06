// Plain Java with the game rules. No Minecraft or Fabric on the classpath.
plugins {
    `java-library`
    // Only exports core's classpath, so Loom can put core in the oboecraft mod when running in dev.
    // It adds no Minecraft or Fabric dependencies. Loom is on the classpath from the root build.
    id("net.fabricmc.fabric-loom-companion")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
