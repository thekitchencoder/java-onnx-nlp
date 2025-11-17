# Gemini Project Overview: NLP ONNX Classifier

This document provides a comprehensive overview of the NLP ONNX Classifier project for the Gemini AI assistant.

## Project Overview

The project is a modern Java library for loading and using ONNX-format NLP classifiers. It is designed with a clean separation of concerns and minimal runtime dependencies. The project is built with Java 21 and Gradle.

The project is structured into two main modules:

*   `nlp-onnx-core`: The core library containing the main functionality for text classification, model loading, calibration, and preprocessing.
*   `nlp-onnx-spring-boot-starter`: A Spring Boot starter for easy integration into Spring applications.

### Key Technologies

*   **Java 21**: The project is written in Java 21.
*   **Gradle**: The project is built with Gradle.
*   **ONNX Runtime**: The project uses the ONNX Runtime for inference.
*   **Spring Boot**: The project provides a Spring Boot starter for easy integration.
*   **JUnit 5**: The project uses JUnit 5 for testing.

## Building and Running

### Building the Project

To build the project, run the following command from the root directory:

```bash
./gradlew build
```

### Running Tests

To run the tests, run the following command from the root directory:

```bash
./gradlew test
```

## Development Conventions

### Coding Style

The project uses modern Java features and follows a clean architecture with a clear separation of concerns between the API, model, and implementation layers.

### Testing

The project uses JUnit 5 for testing. Tests are located in the `src/test/java` directory of each module.

### Contribution Guidelines

Contribution guidelines are not yet defined. See the `CONTRIBUTING.md` file for more information.
