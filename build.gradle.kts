import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin

plugins {
    kotlin("multiplatform") version "2.4.10"
    id("de.infix.testBalloon") version "1.1.0"
}

group = "ru.ought.kotx"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvm()
    js {
        browser()
        nodejs()
    }
    sourceSets {
        commonTest.dependencies {
            implementation("de.infix.testBalloon:testBalloon-framework-core:1.1.0")
            implementation("de.infix.testBalloon:testBalloon-integration-kotest-assertions:1.1.0")
        }
    }
}

plugins.withType<NodeJsPlugin> {
    if (providers.gradleProperty("kotx.useSystemNode").orNull == "true") {
        extensions.configure<NodeJsEnvSpec> { download.set(false) }
    }
}
