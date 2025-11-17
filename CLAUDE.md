# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A modern Java library for loading and running ONNX-format NLP classifiers with clean architectural separation. The codebase is split into two modules:
- `nlp-onnx-core`: Core library with minimal dependencies
- `nlp-onnx-spring-boot-starter`: Spring Boot auto-configuration

## Build Commands

```bash
# Build entire project
./gradlew build

# Build specific module
./gradlew :nlp-onnx-core:build
./gradlew :nlp-onnx-spring-boot-starter:build

# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :nlp-onnx-core:test

# Run a single test class
./gradlew :nlp-onnx-core:test --tests BasicTextPreprocessorTest

# Run a single test method
./gradlew :nlp-onnx-core:test --tests BasicTextPreprocessorTest.shouldLowercaseText

# Clean build
./gradlew clean build

# Publish to local Maven (for testing integration)
./gradlew publishToMavenLocal
```

## Architecture Principles

### Separation of Concerns
The library strictly separates:
1. **API layer** (`uk.codery.onnx.nlp.api`) - Public interfaces that consumers depend on
2. **Model loading** (`uk.codery.onnx.nlp.model`) - Abstracted from deployment environment
3. **Implementation** (`uk.codery.onnx.nlp.impl`) - ONNX Runtime integration
4. **Support components** - Calibration, preprocessing, tokenization (all pluggable)

### Key Design Patterns

**Builder Pattern**: `TextClassifierBuilder` is the primary entry point for constructing classifiers. It handles:
- Model loading via pluggable `ModelLoader` implementations
- Tokenizer selection (from config vocabulary or custom)
- Preprocessor configuration with sensible defaults
- OrtEnvironment lifecycle management (owned vs shared)

**Environment Ownership**: The `ownsEnvironment` flag in `OnnxTextClassifier` determines whether the classifier owns the `OrtEnvironment` and should close it. This is critical for:
- Standalone usage (owns environment, closes on `close()`)
- Spring Boot usage (shared environment bean, doesn't close)

**Calibration Pipeline**: Calibration is applied post-inference via the Strategy pattern:
- `CalibratorFactory` creates the appropriate `Calibrator` from `CalibrationData`
- Supports temperature scaling, Platt scaling, or identity (no-op)
- The `buildResult()` method in `OnnxTextClassifier` applies calibration before returning results

### Model Bundle Structure

Models are loaded as `ModelBundle` objects containing:
1. `modelBytes` - Raw ONNX model (required)
2. `config` - `ModelConfig` with class labels, tensor names, vocabulary (required)
3. `calibration` - `CalibrationData` for score adjustment (optional)

The `FileSystemModelLoader` expects directories with:
```
model/
├── model.onnx          # ONNX model file
├── config.json         # Model configuration (JSON deserialized to ModelConfig)
└── calibration.json    # Optional calibration parameters
```

### Inference Flow

1. **Preprocessing** → `TextPreprocessor.preprocess()` normalizes text
2. **Tokenization** → `Tokenizer.tokenize()` converts to token IDs
3. **ONNX Inference** → `OrtSession.run()` executes model
4. **Calibration** → `Calibrator.calibrate()` adjusts probabilities
5. **Result Building** → Wraps in `ClassificationResult` with all class probabilities

The entire batch flows through `classifyBatch()` - even single classifications call this internally.

## Spring Boot Integration

### Auto-Configuration Lifecycle

`NlpOnnxAutoConfiguration` creates beans in order:
1. `OrtEnvironment` - Shared singleton for all sessions
2. `ModelLoader` - Defaults to `FileSystemModelLoader`
3. `TextPreprocessor` - Configured from `nlp.onnx.preprocessing.*` properties
4. `TextClassifier` - Built via `TextClassifierBuilder`, performs warmup if configured

The `TextClassifier` bean has `destroyMethod = "close"` to ensure proper cleanup.

### Configuration Properties

All properties under `nlp.onnx.*` are bound to `NlpOnnxProperties`:
- `model-path` - Required for auto-configuration to activate
- `warmup` + `warmup-texts` - Optional startup warmup
- `preprocessing.*` - Text preprocessing toggles

Override any bean with `@ConditionalOnMissingBean` to customize behavior.

## Testing Patterns

- Use **AssertJ** for assertions (`assertThat()` style)
- Use **Mockito** for mocking (with JUnit Jupiter extension)
- Test classes follow naming: `{ClassUnderTest}Test.java`
- Test methods follow naming: `should{ExpectedBehavior}`

## Lombok Usage

- `@Value` + `@Builder` for immutable data classes (`ModelConfig`, `ClassificationResult`, etc.)
- `@NonNull` for null-checking parameters (throws `NullPointerException` if violated)
- `@Slf4j` for logging in implementations
- `@RequiredArgsConstructor` for dependency injection
- `@Data` for Spring configuration properties

## Adding New Components

### Custom ModelLoader
Implement `ModelLoader` interface, override both `load(Path)` and `loadFromResource(String)`. Return `ModelBundle` with all required components.

### Custom Calibrator
1. Implement `Calibrator` interface
2. Add case to `CalibratorFactory.create()` switch statement
3. Define calibration type string in `calibration.json`

### Custom Tokenizer
Implement `Tokenizer` interface. The `tokenizeBatch()` default implementation calls `tokenize()` for each text - override if you have a more efficient batch tokenizer.

### Custom Preprocessor
Implement `TextPreprocessor` interface. The `preprocessBatch()` default streams over inputs - override for batch-optimized preprocessing.

## Important Notes

- **Thread Safety**: `OnnxTextClassifier` is thread-safe for concurrent inference (ONNX Runtime sessions are thread-safe)
- **Resource Management**: Always use try-with-resources or explicit `close()` on `TextClassifier` instances
- **Tensor Cleanup**: `OnnxTextClassifier.classifyBatch()` properly closes tensors and results after inference
- **Jackson Dependency**: Required for deserializing `config.json` and `calibration.json` files
- **Java 21**: Leverages Java 21 features (records in examples, pattern matching in switch expressions)

## Python Scripts

The `scripts/` directory contains Python training scripts that generate ONNX models. These are separate from the Java codebase but produce the models consumed by this library. Check `scripts/classifier_*_calibration.json` for examples of calibration formats.
- before runing python commands cd into the scripts folder and source .venv/bin/activate first
- To run the onnx_train.py and onnx_eval.py scripts cd into the scripts directory an use `uv run` rather than `python`