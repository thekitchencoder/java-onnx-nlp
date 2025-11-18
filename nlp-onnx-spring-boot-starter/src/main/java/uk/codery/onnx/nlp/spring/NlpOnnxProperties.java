package uk.codery.onnx.nlp.spring;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for NLP ONNX classifier.
 */
@Data
@ConfigurationProperties(prefix = "nlp.onnx")
public class NlpOnnxProperties {

    /**
     * Whether to enable the NLP ONNX autoconfiguration.
     */
    private boolean enabled = true;

    /**
     * Path to the model directory (for single model configuration).
     * @deprecated Use {@link #models} for multi-model support.
     */
    @Deprecated
    private String modelPath;

    /**
     * Path to custom vocabulary file (optional, for single model configuration).
     * @deprecated Use {@link #models} for multi-model support.
     */
    @Deprecated
    private String vocabularyPath;

    /**
     * Whether to perform model warmup on startup (for single model configuration).
     * @deprecated Use {@link #models} for multi-model support.
     */
    @Deprecated
    private boolean warmup = false;

    /**
     * Sample texts to use for warmup (for single model configuration).
     * @deprecated Use {@link #models} for multi-model support.
     */
    @Deprecated
    private List<String> warmupTexts = new ArrayList<>();

    /**
     * Text preprocessing configuration (for single model configuration).
     * @deprecated Use {@link #models} for multi-model support.
     */
    @Deprecated
    private PreprocessingConfig preprocessing = new PreprocessingConfig();

    /**
     * Multiple model configurations keyed by bean name.
     * Example:
     * <pre>
     * nlp.onnx.models.address.model-path=/path/to/address/model
     * nlp.onnx.models.voda.model-path=/path/to/voda/model
     * </pre>
     */
    private Map<String, ModelConfig> models = new HashMap<>();

    @Data
    public static class PreprocessingConfig {
        private boolean lowercase = true;
        private boolean removeUrls = false;
        private boolean removeEmails = false;
        private boolean removeMentions = false;
        private boolean removeHashtags = false;
        private boolean normalizeWhitespace = true;
        private boolean unicodeNormalization = true;
        private boolean trim = true;
    }

    /**
     * Configuration for a single model.
     */
    @Data
    public static class ModelConfig {
        /**
         * Path to the model directory.
         */
        private String modelPath;

        /**
         * Path to custom vocabulary file (optional).
         */
        private String vocabularyPath;

        /**
         * Whether to perform model warmup on startup.
         */
        private boolean warmup = false;

        /**
         * Sample texts to use for warmup.
         */
        private List<String> warmupTexts = new ArrayList<>();

        /**
         * Text preprocessing configuration for this model.
         * If not specified, uses default preprocessing settings.
         */
        private PreprocessingConfig preprocessing = new PreprocessingConfig();
    }
}
