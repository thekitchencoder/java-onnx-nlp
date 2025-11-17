# NLP ONNX Classifier

A modern Java library for loading and using ONNX-format NLP classifiers with clean separation of concerns and minimal runtime dependencies.

## Features

- Clean API for text classification with ONNX models
- Pluggable model loading (filesystem, classpath, custom sources)
- Built-in calibration support (temperature scaling, Platt scaling)
- Batch processing for efficient inference
- Text preprocessing utilities
- Model warmup for optimal performance
- Spring Boot auto-configuration (separate module)
- Java 21+ with modern language features
- Minimal runtime dependencies

## Project Structure

```
nlp-onnx/
├── nlp-onnx-core/              # Core library
│   ├── api/                     # Public API interfaces
│   ├── model/                   # Model loading abstractions
│   ├── calibration/             # Calibration implementations
│   ├── preprocessing/           # Text preprocessing
│   ├── tokenization/            # Tokenization interfaces
│   └── impl/                    # ONNX Runtime implementation
└── nlp-onnx-spring-boot-starter/ # Spring Boot integration
```

## Architecture

The library follows a clean architecture with clear separation:

1. **API Layer** (`uk.codery.onnx.nlp.api`) - Core abstractions
   - `TextClassifier` - Main classification interface
   - `ClassificationResult` - Result model
   - `TextPreprocessor` - Text preprocessing interface

2. **Model Layer** (`uk.codery.onnx.nlp.model`) - Model loading and configuration
   - `ModelLoader` - Abstraction for loading models
   - `ModelBundle` - Container for model + metadata
   - `ModelConfig` - Model configuration
   - `CalibrationData` - Calibration parameters

3. **Implementation Layer** (`uk.codery.onnx.nlp.impl`) - ONNX Runtime integration
   - `OnnxTextClassifier` - Main implementation

4. **Support Components**
   - Calibration (temperature, Platt scaling)
   - Preprocessing (text normalization, cleaning)
   - Tokenization (pluggable tokenizers)

## Usage

### Core Library

```java
// Load model
ModelLoader loader = new FileSystemModelLoader();
ModelBundle model = loader.load(Path.of("/path/to/model"));

// Create tokenizer (or provide your own)
Tokenizer tokenizer = SimpleWhitespaceTokenizer.fromVocabulary(
    model.getConfig().getVocabulary()
);

// Create classifier
TextClassifier classifier = TextClassifierBuilder.newBuilder()
    .modelLoader(loader)
    .modelPath(Path.of("/path/to/model"))
    .tokenizer(tokenizer)
    .preprocessor(BasicTextPreprocessor.createDefault())
    .build();

// Classify text
ClassificationResult result = classifier.classify("This is a test");
System.out.println("Predicted: " + result.getPredictedLabel());
System.out.println("Confidence: " + result.getConfidence());

// Batch classification
List<String> texts = List.of("text 1", "text 2", "text 3");
List<ClassificationResult> results = classifier.classifyBatch(texts);

// Warmup (optional, improves performance)
classifier.warmup(List.of("sample text 1", "sample text 2"));

// Cleanup
classifier.close();
```

### Spring Boot Integration

Add dependency:
```kotlin
implementation("uk.codery.onnx:nlp-onnx-spring-boot-starter:0.1.0-SNAPSHOT")
```

Configure in `application.yml`:
```yaml
nlp:
  onnx:
    enabled: true
    model-path: /path/to/model
    warmup: true
    warmup-texts:
      - "sample text for warmup"
    preprocessing:
      lowercase: true
      normalize-whitespace: true
      remove-urls: true
```

Use in your application:
```java
@Service
public class ClassificationService {

    private final TextClassifier classifier;

    public ClassificationService(TextClassifier classifier) {
        this.classifier = classifier;
    }

    public String classify(String text) {
        return classifier.classify(text).getPredictedLabel();
    }
}
```

## Model Directory Structure

Your model directory should contain:

```
model/
├── model.onnx           # ONNX model file
├── config.json          # Model configuration
└── calibration.json     # Optional calibration data
```

### config.json Example

```json
{
  "modelName": "sentiment-classifier",
  "version": "1.0",
  "classLabels": ["negative", "neutral", "positive"],
  "inputTensorName": "input",
  "outputTensorName": "output",
  "maxSequenceLength": 512,
  "vocabulary": {
    "[PAD]": 0,
    "[UNK]": 1,
    "the": 2,
    "a": 3
  }
}
```

### calibration.json Example

```json
{
  "calibrationType": "temperature",
  "parameters": {
    "temperature": 1.5
  }
}
```

## Custom Implementations

### Custom Model Loader

```java
public class S3ModelLoader implements ModelLoader {
    @Override
    public ModelBundle load(Path modelPath) throws IOException {
        // Load from S3
    }
}
```

### Custom Tokenizer

```java
public class BertTokenizer implements Tokenizer {
    @Override
    public long[] tokenize(String text, int maxLength) {
        // BERT tokenization logic
    }
}
```

### Custom Preprocessor

```java
public class CustomPreprocessor implements TextPreprocessor {
    @Override
    public String preprocess(String text) {
        // Custom preprocessing logic
    }
}
```

## Building

```bash
./gradlew build
```

## Testing

```bash
./gradlew test
```

## Requirements

- Java 21+
- Gradle 8.6+

## Dependencies

### Core Module
- ONNX Runtime 1.17.1
- SLF4J 2.0.12
- Jackson 2.17.0 (for JSON configuration)
- Lombok 1.18.32

### Spring Boot Starter
- Spring Boot 3.2.3
- Core module

## License

TBD
