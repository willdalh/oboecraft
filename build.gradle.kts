plugins {
    alias(libs.plugins.fabric.loom) apply false
}

allprojects {
    group = "io.github.willdalh"
    version = "0.1.0"
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }
}
