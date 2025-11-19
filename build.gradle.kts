plugins {
    java
    id("io.freefair.lombok") version "8.6" apply false
    `maven-publish`
    signing
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
    apply(plugin = "java-library")
    apply(plugin = "io.freefair.lombok")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<Javadoc> {
        options {
            (this as StandardJavadocDocletOptions).apply {
                addStringOption("Xdoclint:none", "-quiet")
                addBooleanOption("html5", true)
            }
        }
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])

                pom {
                    name.set(project.name)
                    description.set("Java library for loading and running ONNX-format NLP classifiers")
                    url.set("https://github.com/thekitchencoder/java-onnx-nlp")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("thekitchencoder")
                            name.set("The Kitchen Coder")
                            url.set("https://github.com/thekitchencoder")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/thekitchencoder/java-onnx-nlp.git")
                        developerConnection.set("scm:git:ssh://github.com/thekitchencoder/java-onnx-nlp.git")
                        url.set("https://github.com/thekitchencoder/java-onnx-nlp")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "central"
                val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl

                credentials {
                    username = findProperty("mavenUsername") as String? ?: System.getenv("MAVEN_USERNAME")
                    password = findProperty("mavenPassword") as String? ?: System.getenv("MAVEN_PASSWORD")
                }
            }
        }
    }

    configure<SigningExtension> {
        val signingKey = findProperty("signingKey") as String? ?: System.getenv("GPG_PRIVATE_KEY")
        val signingPassword = findProperty("signingPassword") as String? ?: System.getenv("GPG_PASSPHRASE")

        if (signingKey != null && signingPassword != null) {
            useInMemoryPgpKeys(signingKey, signingPassword)
        }

        sign(extensions.getByType<PublishingExtension>().publications["mavenJava"])
    }

    tasks.withType<Sign> {
        onlyIf {
            !version.toString().endsWith("SNAPSHOT")
        }
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
