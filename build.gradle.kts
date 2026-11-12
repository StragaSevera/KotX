import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin

plugins { kotlin("multiplatform") version "2.4.10" }

group = "ru.ought.kotx"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvm()
    js {
        browser()
        nodejs()
    }
    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

plugins.withType<NodeJsPlugin> {
    if (providers.gradleProperty("kotx.useSystemNode").orNull == "true") {
        extensions.configure<NodeJsEnvSpec> { download.set(false) }
    }
}
