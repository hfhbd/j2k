plugins {
    kotlin("jvm")
    id("publish")
}

publishing {
    publications.register<MavenPublication>("mavenJava") {
        from(components["java"])
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
    target {
        compilations.configureEach {
            kotlinSourceSets.forAll {
                it.languageSettings.progressiveMode = true
            }
            kotlinOptions {
                // allWarningsAsErrors = true
            }
        }
    }
}
