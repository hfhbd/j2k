pluginManagement { 
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
        gradlePluginPortal()
    }
}

plugins {
    id("MyRepos")
    id("com.gradle.enterprise") version "3.15"
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        if (System.getenv("CI") != null) {
            publishAlways()
            tag("CI")
        }
    }
}

rootProject.name = "j2k"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

include(":j2k-core")
include(":j2k-cli")

include(":j2k-intellij-env")
include(":j2k-kotlin-psi")
