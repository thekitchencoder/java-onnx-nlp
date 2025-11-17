plugins {
    java
    id("io.freefair.lombok") version "8.6" apply false
}

allprojects {
    group = "uk.codery.onnx"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.freefair.lombok")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    dependencies {
        val lombokVersion = "1.18.32"

        // Test dependencies
        testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
        testImplementation("org.assertj:assertj-core:3.25.3")
        testImplementation("org.mockito:mockito-core:5.11.0")
        testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    }
}
