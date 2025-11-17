package uk.codery.onnx.nlp;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import lombok.NonNull;
import uk.codery.onnx.nlp.api.TextClassifier;
import uk.codery.onnx.nlp.api.TextPreprocessor;
import uk.codery.onnx.nlp.impl.OnnxTextClassifier;
import uk.codery.onnx.nlp.model.ModelBundle;
import uk.codery.onnx.nlp.model.ModelLoader;
import uk.codery.onnx.nlp.preprocessing.BasicTextPreprocessor;
import uk.codery.onnx.nlp.tokenization.SimpleWhitespaceTokenizer;
import uk.codery.onnx.nlp.tokenization.Tokenizer;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Builder for creating TextClassifier instances.
 */
public class TextClassifierBuilder {

    private ModelLoader modelLoader;
    private Path modelPath;
    private String resourceName;
    private Tokenizer tokenizer;
    private TextPreprocessor preprocessor;
    private OrtEnvironment environment;
    private boolean ownsEnvironment = true;

    /**
     * Sets the model loader to use.
     */
    public TextClassifierBuilder modelLoader(@NonNull ModelLoader modelLoader) {
        this.modelLoader = modelLoader;
        return this;
    }

    /**
     * Sets the model path to load from.
     */
    public TextClassifierBuilder modelPath(@NonNull Path modelPath) {
        this.modelPath = modelPath;
        return this;
    }

    /**
     * Sets the resource name to load from.
     */
    public TextClassifierBuilder resourceName(@NonNull String resourceName) {
        this.resourceName = resourceName;
        return this;
    }

    /**
     * Sets the tokenizer to use.
     */
    public TextClassifierBuilder tokenizer(@NonNull Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
        return this;
    }

    /**
     * Sets the text preprocessor to use.
     */
    public TextClassifierBuilder preprocessor(@NonNull TextPreprocessor preprocessor) {
        this.preprocessor = preprocessor;
        return this;
    }

    /**
     * Sets a shared ONNX Runtime environment.
     */
    public TextClassifierBuilder environment(@NonNull OrtEnvironment environment) {
        this.environment = environment;
        this.ownsEnvironment = false;
        return this;
    }

    /**
     * Builds the TextClassifier instance.
     *
     * @throws IOException if model loading fails
     * @throws OrtException if ONNX Runtime initialization fails
     */
    public TextClassifier build() throws IOException, OrtException {
        // Validate
        if (modelLoader == null) {
            throw new IllegalStateException("ModelLoader must be set");
        }

        if (modelPath == null && resourceName == null) {
            throw new IllegalStateException("Either modelPath or resourceName must be set");
        }

        // Load model
        ModelBundle modelBundle = modelPath != null
                ? modelLoader.load(modelPath)
                : modelLoader.loadFromResource(resourceName);

        // Setup tokenizer
        Tokenizer effectiveTokenizer = tokenizer;
        if (effectiveTokenizer == null && modelBundle.getConfig().getVocabulary() != null) {
            // Try to create from model config vocabulary if available
            effectiveTokenizer = SimpleWhitespaceTokenizer.fromVocabulary(
                    modelBundle.getConfig().getVocabulary()
            );
        }
        // Note: tokenizer can be null for STRING-input models (e.g., TF-IDF)

        // Setup preprocessor
        TextPreprocessor effectivePreprocessor = preprocessor != null
                ? preprocessor
                : BasicTextPreprocessor.createDefault();

        // Setup environment
        OrtEnvironment effectiveEnvironment = environment != null
                ? environment
                : OrtEnvironment.getEnvironment();

        // Build classifier
        return new OnnxTextClassifier(
                effectiveEnvironment,
                modelBundle,
                effectiveTokenizer,
                effectivePreprocessor,
                ownsEnvironment
        );
    }

    /**
     * Creates a new builder instance.
     */
    public static TextClassifierBuilder newBuilder() {
        return new TextClassifierBuilder();
    }
}
