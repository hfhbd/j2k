plugins {
    id("setup")
}

dependencies {
    implementation(projects.j2kIntellijEnv) {
        targetConfiguration = "shadow"
    }
    implementation(projects.j2kKotlinPsi) {
        targetConfiguration = "shadow"
    }

    testImplementation(kotlin("test"))
}

kotlin.jvmToolchain(17)
