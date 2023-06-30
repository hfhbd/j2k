plugins {
    id("setup")
}

dependencies {
    api(projects.j2kCore)

    testImplementation(kotlin("test"))
}

kotlin.jvmToolchain(17)
