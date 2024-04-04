plugins {
    id("setup")
    id("com.github.johnrengelman.shadow")
}

dependencies {
    shadow(platform(libs.kotlin.bom))
    shadow(libs.bundles.idea)
    shadow(libs.j2k.new)
    shadow(libs.j2k.old)
    shadow(libs.java.impl)
    shadow(libs.java.analysis.impl)
    shadow(libs.java.psi)
    shadow(libs.java.psi.impl)
    shadow(libs.kotlin.core)
    shadow(libs.kotlin.base.compiler.configuration)
    shadow(libs.kotlin.base.psi)
    shadow(libs.kotlin.base.plugin)
    shadow(libs.kotlin.base.indices)
    shadow(libs.kotlin.base.analysis)
    shadow(libs.kotlin.base.fe10.analysis)
    shadow(libs.kotlin.base.scripting)
    shadow(libs.kotlin.base.project.structure)
    shadow(libs.intellij.editor)
    shadow(libs.intellij.testFramework) {
        isTransitive = false
    }
    shadow("org.jetbrains.kotlin:kotlin-tooling-core:1.9.23")
    shadow("org.jetbrains.kotlin:kotlin-compiler-for-ide:1.9.20-506") {
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
    exclude(group = "org.jetbrains.kotlin", module = "protobuf-relocated")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-daemon-client")
    exclude(group = "org.jetbrains.kotlin", module = "daemon-common")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-preloader")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-runner-unshaded")
    exclude(group = "org.jetbrains.kotlin", module = "legacy-fir-tests")
    exclude(group = "org.jetbrains.kotlin", module = "analysis-tests")
    exclude(group = "org.jetbrains.kotlin", module = "fir2ir")
    exclude(group = "org.jetbrains.kotlin", module = "jvm-backend")
    exclude(group = "org.jetbrains.kotlin", module = "ir.tree")
    exclude(group = "org.jetbrains.kotlin", module = "ir.serialization.common")
    exclude(group = "org.jetbrains.kotlin", module = "ir.serialization.jvm")
    exclude(group = "org.jetbrains.kotlin", module = "ir.backend.common")
    exclude(group = "org.jetbrains.kotlin", module = "ir.interpreter")
    exclude(group = "org.jetbrains.kotlin", module = "wasm.ir")
    exclude(group = "org.jetbrains.kotlin", module = "compiler")
    exclude(group = "org.jetbrains.kotlin", module = "descriptors.runtime")
    exclude(group = "org.jetbrains.kotlin", module = "descriptors")
    exclude(group = "org.jetbrains.kotlin", module = "descriptors.jvm")
    exclude(group = "org.jetbrains.kotlin", module = "light-classes")
    exclude(group = "org.jetbrains.kotlin", module = "resolution")
    exclude(group = "org.jetbrains.kotlin", module = "serialization")
    exclude(group = "org.jetbrains.kotlin", module = "frontend")

    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
}

tasks.shadowJar {
    isZip64 = true
    archiveClassifier.set("")
    configurations = listOf(project.configurations.shadow.get())

    include("*.jar")
    include("misc/*.properties")

    include("org/intellij/**")
    include("com/intellij/**")
    include("org/jetbrains/**")

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
