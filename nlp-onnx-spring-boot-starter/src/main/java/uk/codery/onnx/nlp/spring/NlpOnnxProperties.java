package uk.codery.onnx.nlp.spring;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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
     * Text preprocessing configuration.
     */
    private PreprocessingConfig preprocessing = new PreprocessingConfig();

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
}
