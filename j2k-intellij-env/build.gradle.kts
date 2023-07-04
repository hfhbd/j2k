plugins {
    id("setup")
    id("com.github.johnrengelman.shadow")
}

dependencies {
    shadow(libs.bundles.idea)
    shadow(libs.j2k.new)
    shadow(libs.j2k.old)
    shadow(libs.java)
    shadow(libs.java.impl)
    shadow(libs.kotlin.core)
    shadow(libs.kotlin.base.psi)
    shadow(libs.intellij.editor)
    shadow(libs.intellij.testFramework) {
        isTransitive = false
    }
}

configurations.shadow {
    exclude(group = "com.jetbrains.rd")
    exclude(group = "com.github.jetbrains", module = "jetCheck")
    exclude(group = "com.jetbrains.intellij.platform", module = "wsl-impl")
    exclude(group = "com.jetbrains.infra")
    exclude(group = "org.roaringbitmap")
    exclude(group = "ai.grazie.spell")
    exclude(group = "ai.grazie.model")
    exclude(group = "ai.grazie.utils")
    exclude(group = "ai.grazie.nlp")
}

tasks.shadowJar {
    archiveClassifier.set("")
    configurations = listOf(project.configurations.shadow.get())

    include("*.jar")
    include("misc/*.properties")

    include("org/intellij/**")
    include("com/intellij/**")
    include("org/jetbrains/kotlin/idea/compiler/configuration/**")

    include("org/picocontainer/**")
    include("it/unimi/**")
    include("org/jdom/**")
    include("com/github/benmanes/**")

    include("messages/*.properties")
    include("gnu/**")
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
