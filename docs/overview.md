### NLP ONNX Classifier — Project Overview

This repository provides a modern Java library for loading and serving NLP text classification models exported to ONNX format. It is designed with a clean separation of concerns, minimal runtime dependencies, and optional Spring Boot auto‑configuration for easy integration.

#### Key Features

- Clean, minimal API for text classification (`TextClassifier`, `ClassificationResult`)
- Pluggable model loading (filesystem by default; classpath/remote can be added)
- ONNX Runtime integration with session optimization
- Probability calibration (temperature scaling, Platt scaling)
- Text preprocessing utilities (configurable normalization/cleaning)
- Pluggable tokenization (simple whitespace tokenizer with model‑provided vocabulary)
- Batch processing and warmup to optimize latency/throughput
- Spring Boot autoconfiguration module
- Java 21+, Lombok for concise models

---

### Repository Layout

```
nlp-onnx/
├── nlp-onnx-core/                 # Core library (public API + implementation)
│   └── src/main/java/uk/codery/onnx/nlp/
│       ├── api/                   # Public API interfaces and result types
│       ├── model/                 # Model loading/config/calibration metadata
│       ├── calibration/           # Probability calibration implementations
│       ├── preprocessing/         # Text preprocessing utilities
│       ├── tokenization/          # Tokenization interfaces/implementations
│       └── impl/                  # ONNX Runtime classifier implementation
├── nlp-onnx-spring-boot-starter/  # Spring Boot autoconfiguration
├── examples/                      # Minimal runnable examples
├── scripts/                       # Python training/evaluation utilities (optional)
└── README.md                      # Top-level quickstart and architecture
```

---

### Core Concepts and Architecture

The project follows a layered architecture with clear boundaries:

1) API Layer — `uk.codery.onnx.nlp.api`
   - `TextClassifier` — The main inference interface. Thread‑safe. Provides `classify`, `classifyBatch`, `warmup`, and `close`.
   - `ClassificationResult` — Immutable result with `predictedLabel`, `confidence`, `calibrated`, and per‑class probabilities.
   - `TextPreprocessor` — Single method `preprocess(String)` to transform raw input text before tokenization/inference.

2) Model Layer — `uk.codery.onnx.nlp.model`
   - `ModelLoader` — Abstraction for loading a `ModelBundle` from a path or resource.
   - `FileSystemModelLoader` — Loads model artifacts from the filesystem.
   - `ModelBundle` — Holds `byte[]` ONNX model, `ModelConfig`, and optional calibration data.
   - `ModelConfig` — Model metadata (model name, version, class labels, input/output tensor names, max sequence length, optional vocabulary, and extra metadata).
   - `CalibrationData` — Parameters used by calibrators.

3) Implementation Layer — `uk.codery.onnx.nlp.impl`
   - `OnnxTextClassifier` — Bridges the API to ONNX Runtime (`OrtEnvironment`, `OrtSession`).
     - Detects input tensor type (STRING vs LONG) to support both string‑based and tokenized integer inputs.
     - Applies preprocessing and (optionally) tokenization before inference.
     - Applies calibration to raw probabilities if present.
     - Uses ONNX Runtime session optimization `ALL_OPT` for performance.

4) Support Components
   - Calibration — `uk.codery.onnx.nlp.calibration` provides `Calibrator`, `CalibratorFactory`, and implementations (e.g., Platt scaling, Temperature scaling, Identity).
   - Preprocessing — `uk.codery.onnx.nlp.preprocessing.BasicTextPreprocessor` with configurable steps: lowercase, URL/email/mention/hashtag removal, whitespace normalization, Unicode normalization, trimming. Includes `createDefault()` convenience factory.
   - Tokenization — `uk.codery.onnx.nlp.tokenization.Tokenizer` interface and `SimpleWhitespaceTokenizer` implementation (can be built from `ModelConfig` vocabulary).

Builder — `uk.codery.onnx.nlp.TextClassifierBuilder`

- Fluent builder that wires together `ModelLoader`, model path/resource, preprocessor, tokenizer, and an optional shared `OrtEnvironment`.
- If no tokenizer is supplied and the `ModelConfig` provides a vocabulary, the builder creates a `SimpleWhitespaceTokenizer` automatically.
- Defaults to `BasicTextPreprocessor.createDefault()` when none is provided.
- Can accept a shared `OrtEnvironment` or construct/own one (and close it when the classifier is closed).

---

### Typical Usage (Core Library)

```java
ModelLoader loader = new FileSystemModelLoader();
Path modelPath = Path.of("/path/to/model");

try (TextClassifier classifier = TextClassifierBuilder.newBuilder()
        .modelLoader(loader)
        .modelPath(modelPath)
        .preprocessor(BasicTextPreprocessor.createDefault())
        // .tokenizer(customTokenizer) // optional; often inferred from ModelConfig
        .build()) {

    // Optional warmup
    classifier.warmup(List.of("warmup 1", "warmup 2"));

    // Single inference
    ClassificationResult r = classifier.classify("This is a great product!");
    System.out.println(r.getPredictedLabel() + " (" + r.getConfidence() + ")");

    // Batch inference (more efficient)
    List<ClassificationResult> rs = classifier.classifyBatch(List.of("a", "b", "c"));
}
```

See `examples/CoreUsageExample.java` for a fully runnable example.

---

### Spring Boot Integration

Module: `nlp-onnx-spring-boot-starter`

- `NlpOnnxAutoConfiguration` exposes beans for `OrtEnvironment`, `ModelLoader`, `TextPreprocessor`, and the `TextClassifier` itself.
- Controlled by properties under `nlp.onnx.*` (see `NlpOnnxProperties`). Key properties:
  - `nlp.onnx.enabled` (boolean, default true)
  - `nlp.onnx.model-path` (required to create the classifier bean)
  - `nlp.onnx.warmup` and `nlp.onnx.warmup-texts` for startup warmup
  - `nlp.onnx.preprocessing.*` toggles for the `BasicTextPreprocessor`
  - `nlp.onnx.vocabulary-path` (reserved for future custom tokenizer wiring)

Example `application.yml` (see `examples/spring-boot-application.yml` for an extended version):

```yaml
nlp:
  onnx:
    enabled: true
    model-path: /absolute/path/to/model
    warmup: true
    warmup-texts:
      - "sample warmup"
    preprocessing:
      lowercase: true
      normalize-whitespace: true
      remove-urls: true
```

---

### Model Bundles and Configuration

Models are loaded as a `ModelBundle`, which includes:

- The ONNX model bytes (from a directory or resource)
- `ModelConfig` with:
  - `modelName`, `version`
  - `classLabels` (ordered list; positions map to output indices)
  - `inputTensorName`, `outputTensorName` (defaults: `input`, `output`)
  - `maxSequenceLength`
  - Optional `vocabulary` for tokenization
  - Optional additional `metadata`
- Optional `CalibrationData` used to calibrate raw probabilities

If the ONNX model expects STRING inputs, tokenization can be skipped; otherwise, provide or infer a `Tokenizer`.

---

### Calibration

Calibration refines raw classifier probabilities to better reflect true likelihoods. The core supports:

- Platt scaling (sigmoid a·x + b)
- Temperature scaling
- Identity (no calibration) when calibration is absent or not applicable

Calibration parameters can be generated by the Python scripts (see below) and are applied automatically at inference when present in the `ModelBundle`.

---

### Preprocessing and Tokenization

- Preprocessing via `BasicTextPreprocessor` is fully configurable and enabled by default (lowercasing, whitespace normalization, trimming). URL/email/mention/hashtag removal and Unicode normalization can be toggled.
- Tokenization is pluggable via the `Tokenizer` interface. A simple whitespace tokenizer is provided and can be constructed from a vocabulary in `ModelConfig`.

---

### Performance Considerations

- Batch classification (`classifyBatch`) typically yields higher throughput than many single calls.
- Use `warmup` with representative texts at startup to prime JIT and ONNX Runtime caches.
- Reuse a shared `OrtEnvironment` across classifiers/requests in high‑throughput services (the builder can accept an external environment and avoid owning/closing it).
- The ONNX session is created with `ALL_OPT` optimization level.

---

### Examples and Test Harness

- Examples
  - `examples/CoreUsageExample.java` — Minimal end‑to‑end usage of the core library.
  - `examples/SpringBootUsageExample.java` — Illustrative Spring Boot usage.

- Standalone Test Harness
  - `nlp-onnx-core/src/test/java/uk/codery/onnx/nlp/harness/ModelTestHarness.java`
  - Loads models located under `scripts/models/<name>` (e.g., `address`, `voda`, `risk`) and runs them on `scripts/example/test_data.txt`.
  - Useful for quick manual validation of multiple heads and printing per‑class probabilities.

---

### Python Scripts (Training and Evaluation)

The `scripts/` folder contains optional Python utilities to train logistic regression text classifiers (TF‑IDF features) and export them to ONNX, plus evaluation helpers.

Key docs: `scripts/README.md`

- `onnx_train.py` — Trains one or more heads from a CSV with a `text` column and `label_<head>` columns. Produces:
  - `<name>_<head>.onnx` — The ONNX model
  - `<name>_<head>_calibration.json` — Platt scaling parameters per head
  - `<name>_summary.json` — Mapping head → ONNX and calibration paths
- `onnx_eval.py` — Evaluates exported models on free‑form inputs (JSON Lines or CSV output). Supports thresholds and calibration.

These scripts are optional and not required to use the Java library with pre‑existing ONNX models.

---

### Building and Requirements

- Java 21+
- Build tool: Gradle Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`)
- Lombok used for boilerplate in model/value classes
- ONNX Runtime Java dependency for inference

Build from the repository root:

```bash
./gradlew build
```

---

### Quick Start Checklist

1) Obtain or train an ONNX text classification model and its `ModelConfig`/calibration files.
2) Add `nlp-onnx-core` as a dependency (or use the monorepo modules directly).
3) Create a `TextClassifier` via `TextClassifierBuilder`, pointing it at your model path.
4) Optionally provide a tokenizer (or let the builder infer one from the model vocabulary).
5) Call `classify`/`classifyBatch`. Consider `warmup` on startup.
6) In Spring Boot apps, set `nlp.onnx.model-path` and enable the autoconfiguration.

---

### Where to Look in the Code

- Public API: `nlp-onnx-core/src/main/java/uk/codery/onnx/nlp/api/`
- Builder: `nlp-onnx-core/src/main/java/uk/codery/onnx/nlp/TextClassifierBuilder.java`
- ONNX implementation: `nlp-onnx-core/src/main/java/uk/codery/onnx/nlp/impl/OnnxTextClassifier.java`
- Model abstractions: `nlp-onnx-core/src/main/java/uk/codery/onnx/nlp/model/`
- Preprocessing: `nlp-onnx-core/src/main/java/uk/codery/onnx/nlp/preprocessing/`
- Tokenization: `nlp-onnx-core/src/main/java/uk/codery/onnx/nlp/tokenization/`
- Spring Boot starter: `nlp-onnx-spring-boot-starter/src/main/java/uk/codery/onnx/nlp/spring/`
- Examples: `examples/`
- Scripts and sample data: `scripts/`

---

### Notes and Limitations

- Custom tokenizer wiring from a vocabulary file in Spring Boot is logged as “not yet implemented” — you can still provide a tokenizer at build time in custom configurations.
- If your ONNX model expects STRING inputs, tokenization is optional; otherwise, provide a tokenizer aligned with the model’s training pipeline.
- The repository includes test utilities and an integration test; extend them to fit additional models or heads.

---

If you have questions or need integration guidance, start with the top‑level `README.md` and the example programs, then explore the classes referenced above.
