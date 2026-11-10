plugins {
    alias(libs.plugins.fabric.loom) apply false
}

allprojects {
    group = "io.github.willdalh"
}

subprojects {
    // Each module applies Java itself (java-library in core, Loom in fabric). Only the toolchain is shared.
    plugins.withType<JavaPlugin> {
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
        }
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(25)
            }
        }
    }
}
