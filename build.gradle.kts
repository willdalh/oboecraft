plugins {
    alias(libs.plugins.fabric.loom) apply false
}

allprojects {
    group = "io.github.willdalh"
}

subprojects {
    // Each module applies Java itself (java-library in core, Loom in fabric). Only the toolchain is shared.
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(25)
            }
        }
    }
}
