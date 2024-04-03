plugins {
    id("setup")
    id("java-test-fixtures")
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(projects.j2kIntellijEnv) {
        targetConfiguration = "shadow"
    }

    testImplementation(kotlin("test"))
}
