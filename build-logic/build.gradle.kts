plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.plugins.kotlin.jvm.toDep())
    implementation(libs.plugins.publish.toDep())
    implementation(libs.plugins.shadow.toDep())
}

fun Provider<PluginDependency>.toDep() = map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}

kotlin.jvmToolchain(17)
