plugins {
    `java-library`
}

dependencies {
    // ONNX Runtime
    api("com.microsoft.onnxruntime:onnxruntime:1.17.1")

    // JSON processing for model configuration
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    // Logging facade
    implementation("org.slf4j:slf4j-api:2.0.12")

    // Nullable annotations
    compileOnly("org.jetbrains:annotations:24.1.0")
}
