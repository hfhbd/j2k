plugins {
    id("setup")
}

dependencies {
    implementation(projects.j2kIntellijEnv) {
        targetConfiguration = "shadow"
    }

    testImplementation(kotlin("test"))
}

kotlin.jvmToolchain(17)
