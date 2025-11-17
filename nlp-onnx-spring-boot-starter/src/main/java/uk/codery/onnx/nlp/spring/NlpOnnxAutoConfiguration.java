package uk.codery.onnx.nlp.spring;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import uk.codery.onnx.nlp.api.TextClassifier;
import uk.codery.onnx.nlp.api.TextPreprocessor;
import uk.codery.onnx.nlp.TextClassifierBuilder;
import uk.codery.onnx.nlp.model.FileSystemModelLoader;
import uk.codery.onnx.nlp.model.ModelLoader;
import uk.codery.onnx.nlp.preprocessing.BasicTextPreprocessor;
import uk.codery.onnx.nlp.tokenization.SimpleWhitespaceTokenizer;
import uk.codery.onnx.nlp.tokenization.Tokenizer;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Spring Boot autoconfiguration for NLP ONNX classifier.
 */
@AutoConfiguration
@EnableConfigurationProperties(NlpOnnxProperties.class)
@ConditionalOnProperty(prefix = "nlp.onnx", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class NlpOnnxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OrtEnvironment ortEnvironment() {
        log.info("Creating ONNX Runtime environment");
        return OrtEnvironment.getEnvironment();
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelLoader modelLoader() {
        return new FileSystemModelLoader();
    }

    @Bean
    @ConditionalOnMissingBean
    public TextPreprocessor textPreprocessor(NlpOnnxProperties properties) {
        NlpOnnxProperties.PreprocessingConfig config = properties.getPreprocessing();

        return BasicTextPreprocessor.builder()
                .lowercase(config.isLowercase())
                .removeUrls(config.isRemoveUrls())
                .removeEmails(config.isRemoveEmails())
                .removeMentions(config.isRemoveMentions())
                .removeHashtags(config.isRemoveHashtags())
                .normalizeWhitespace(config.isNormalizeWhitespace())
                .unicodeNormalization(config.isUnicodeNormalization())
                .trim(config.isTrim())
                .build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "nlp.onnx", name = "model-path")
    public TextClassifier textClassifier(
            NlpOnnxProperties properties,
            OrtEnvironment environment,
            ModelLoader modelLoader,
            TextPreprocessor preprocessor
    ) throws IOException, OrtException {
        log.info("Creating TextClassifier from model path: {}", properties.getModelPath());

        TextClassifierBuilder builder = TextClassifierBuilder.newBuilder()
                .modelLoader(modelLoader)
                .modelPath(Paths.get(properties.getModelPath()))
                .preprocessor(preprocessor)
                .environment(environment);

        // Add custom tokenizer if vocabulary is provided
        if (properties.getVocabularyPath() != null) {
            // Load vocabulary and create tokenizer
            // For now, this is left for custom implementation
            log.warn("Custom vocabulary loading not yet implemented");
        }

        TextClassifier classifier = builder.build();

        // Warmup if configured
        if (properties.isWarmup() && !properties.getWarmupTexts().isEmpty()) {
            log.info("Warming up classifier with {} sample texts", properties.getWarmupTexts().size());
            classifier.warmup(properties.getWarmupTexts());
        }

        return classifier;
    }
}
