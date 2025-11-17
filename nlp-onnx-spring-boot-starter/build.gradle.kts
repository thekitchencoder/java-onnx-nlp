plugins {
    `java-library`
}

dependencies {
    // Core module
    api(project(":nlp-onnx-core"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter:3.2.3")
    implementation("org.springframework.boot:spring-boot-autoconfigure:3.2.3")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.2.3")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.3")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
