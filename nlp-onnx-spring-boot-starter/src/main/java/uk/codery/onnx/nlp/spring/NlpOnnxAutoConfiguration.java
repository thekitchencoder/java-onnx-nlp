package uk.codery.onnx.nlp.spring;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import uk.codery.onnx.nlp.api.TextClassifier;
import uk.codery.onnx.nlp.api.TextPreprocessor;
import uk.codery.onnx.nlp.TextClassifierBuilder;
import uk.codery.onnx.nlp.model.FileSystemModelLoader;
import uk.codery.onnx.nlp.model.ModelLoader;
import uk.codery.onnx.nlp.preprocessing.BasicTextPreprocessor;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Spring Boot autoconfiguration for NLP ONNX classifier.
 *
 * Supports both single model (legacy) and multiple named models configuration.
 *
 * <p>Single model configuration (deprecated):
 * <pre>
 * nlp.onnx.model-path=/path/to/model
 * </pre>
 *
 * <p>Multiple models configuration:
 * <pre>
 * nlp.onnx.models.address.model-path=/path/to/address/model
 * nlp.onnx.models.voda.model-path=/path/to/voda/model
 * nlp.onnx.models.risk.model-path=/path/to/risk/model
 * </pre>
 *
 * <p>Inject by name using {@code @Qualifier}:
 * <pre>
 * {@literal @}Autowired
 * {@literal @}Qualifier("addressClassifier")
 * private TextClassifier addressClassifier;
 * </pre>
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
        return createPreprocessor(config);
    }

    /**
     * Legacy single model configuration.
     * Only created if model-path is specified and no models map is configured.
     */
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

        if (properties.getVocabularyPath() != null) {
            log.warn("Custom vocabulary loading not yet implemented");
        }

        TextClassifier classifier = builder.build();

        if (properties.isWarmup() && !properties.getWarmupTexts().isEmpty()) {
            log.info("Warming up classifier with {} sample texts", properties.getWarmupTexts().size());
            classifier.warmup(properties.getWarmupTexts());
        }

        return classifier;
    }

    /**
     * Registers multiple TextClassifier beans from the models configuration.
     */
    @Bean
    public static MultiModelBeanRegistrar multiModelBeanRegistrar() {
        return new MultiModelBeanRegistrar();
    }

    static TextPreprocessor createPreprocessor(NlpOnnxProperties.PreprocessingConfig config) {
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

    /**
     * Post-processor that registers TextClassifier beans for each model in the models map.
     */
    @Slf4j
    static class MultiModelBeanRegistrar implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

        private Environment environment;

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            // Bean definitions are registered in postProcessBeanFactory
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            NlpOnnxProperties properties = Binder.get(environment)
                    .bind("nlp.onnx", NlpOnnxProperties.class)
                    .orElse(new NlpOnnxProperties());

            if (properties.getModels().isEmpty()) {
                return;
            }

            OrtEnvironment ortEnvironment = beanFactory.getBean(OrtEnvironment.class);
            ModelLoader modelLoader = beanFactory.getBean(ModelLoader.class);

            for (Map.Entry<String, NlpOnnxProperties.ModelConfig> entry : properties.getModels().entrySet()) {
                String name = entry.getKey();
                NlpOnnxProperties.ModelConfig modelConfig = entry.getValue();

                if (modelConfig.getModelPath() == null) {
                    log.warn("Model '{}' has no model-path configured, skipping", name);
                    continue;
                }

                try {
                    TextClassifier classifier = createClassifier(name, modelConfig, ortEnvironment, modelLoader);
                    String beanName = name + "Classifier";
                    beanFactory.registerSingleton(beanName, classifier);
                    log.info("Registered TextClassifier bean '{}' for model at: {}", beanName, modelConfig.getModelPath());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create classifier for model: " + name, e);
                }
            }
        }

        private TextClassifier createClassifier(
                String name,
                NlpOnnxProperties.ModelConfig modelConfig,
                OrtEnvironment environment,
                ModelLoader modelLoader
        ) throws IOException, OrtException {
            TextPreprocessor preprocessor = createPreprocessor(modelConfig.getPreprocessing());

            TextClassifierBuilder builder = TextClassifierBuilder.newBuilder()
                    .modelLoader(modelLoader)
                    .modelPath(Paths.get(modelConfig.getModelPath()))
                    .preprocessor(preprocessor)
                    .environment(environment);

            if (modelConfig.getVocabularyPath() != null) {
                log.warn("Custom vocabulary loading not yet implemented for model: {}", name);
            }

            TextClassifier classifier = builder.build();

            if (modelConfig.isWarmup() && !modelConfig.getWarmupTexts().isEmpty()) {
                log.info("Warming up classifier '{}' with {} sample texts", name, modelConfig.getWarmupTexts().size());
                classifier.warmup(modelConfig.getWarmupTexts());
            }

            return classifier;
        }
    }
}
