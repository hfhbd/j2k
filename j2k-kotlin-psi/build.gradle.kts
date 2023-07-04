plugins {
    id("setup")
    id("com.github.johnrengelman.shadow")
}

dependencies {
    shadow(libs.kotlin.compiler)
}

tasks.shadowJar {
    archiveClassifier.set("")
    configurations = listOf(project.configurations.shadow.get())

    include("org/intellij/kotlin/psi/**")
}

tasks.jar {
    enabled = false
}

configurations {
    apiElements {
        outgoing.artifacts.removeIf { tasks.jar.name in it.buildDependencies.getDependencies(null).map { it.name } }
        outgoing.artifact(tasks.shadowJar)
    }
    runtimeElements {
        outgoing.artifacts.removeIf { tasks.jar.name in it.buildDependencies.getDependencies(null).map { it.name } }
        outgoing.artifact(tasks.shadowJar)
    }
}
