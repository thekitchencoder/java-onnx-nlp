package uk.codery.onnx.nlp.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.codery.onnx.nlp.api.ClassificationResult;
import uk.codery.onnx.nlp.api.CompositeClassificationResult;
import uk.codery.onnx.nlp.api.TextClassifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultCompositeTextClassifierTest {

    @Mock
    private TextClassifier addressClassifier;

    @Mock
    private TextClassifier riskClassifier;

    private DefaultCompositeTextClassifier compositeClassifier;

    @BeforeEach
    void setUp() {
        Map<String, TextClassifier> classifiers = new LinkedHashMap<>();
        classifiers.put("address", addressClassifier);
        classifiers.put("risk", riskClassifier);
        compositeClassifier = new DefaultCompositeTextClassifier(classifiers, false);
    }

    @AfterEach
    void tearDown() {
        if (compositeClassifier != null) {
            compositeClassifier.close();
        }
    }

    @Test
    void shouldClassifyWithAllClassifiers() {
        String text = "test input";
        ClassificationResult addressResult = createResult("valid", 0.9);
        ClassificationResult riskResult = createResult("low", 0.85);

        when(addressClassifier.classify(text)).thenReturn(addressResult);
        when(riskClassifier.classify(text)).thenReturn(riskResult);

        CompositeClassificationResult result = compositeClassifier.classify(text);

        assertThat(result.getText()).isEqualTo(text);
        assertThat(result.getResults()).hasSize(2);
        assertThat(result.getResult("address")).isEqualTo(addressResult);
        assertThat(result.getResult("risk")).isEqualTo(riskResult);
    }

    @Test
    void shouldReturnPredictedLabels() {
        String text = "test input";
        when(addressClassifier.classify(text)).thenReturn(createResult("valid", 0.9));
        when(riskClassifier.classify(text)).thenReturn(createResult("low", 0.85));

        CompositeClassificationResult result = compositeClassifier.classify(text);

        Map<String, String> labels = result.getPredictedLabels();
        assertThat(labels).containsEntry("address", "valid");
        assertThat(labels).containsEntry("risk", "low");
    }

    @Test
    void shouldReturnConfidences() {
        String text = "test input";
        when(addressClassifier.classify(text)).thenReturn(createResult("valid", 0.9));
        when(riskClassifier.classify(text)).thenReturn(createResult("low", 0.85));

        CompositeClassificationResult result = compositeClassifier.classify(text);

        Map<String, Double> confidences = result.getConfidences();
        assertThat(confidences.get("address")).isEqualTo(0.9);
        assertThat(confidences.get("risk")).isEqualTo(0.85);
    }

    @Test
    void shouldClassifyBatch() {
        List<String> texts = List.of("text1", "text2");

        when(addressClassifier.classifyBatch(texts)).thenReturn(List.of(
                createResult("valid", 0.9),
                createResult("invalid", 0.7)
        ));
        when(riskClassifier.classifyBatch(texts)).thenReturn(List.of(
                createResult("low", 0.85),
                createResult("high", 0.95)
        ));

        List<CompositeClassificationResult> results = compositeClassifier.classifyBatch(texts);

        assertThat(results).hasSize(2);

        // First text
        assertThat(results.get(0).getText()).isEqualTo("text1");
        assertThat(results.get(0).getResult("address").getPredictedLabel()).isEqualTo("valid");
        assertThat(results.get(0).getResult("risk").getPredictedLabel()).isEqualTo("low");

        // Second text
        assertThat(results.get(1).getText()).isEqualTo("text2");
        assertThat(results.get(1).getResult("address").getPredictedLabel()).isEqualTo("invalid");
        assertThat(results.get(1).getResult("risk").getPredictedLabel()).isEqualTo("high");
    }

    @Test
    void shouldReturnEmptyListForEmptyBatch() {
        List<CompositeClassificationResult> results = compositeClassifier.classifyBatch(List.of());
        assertThat(results).isEmpty();
    }

    @Test
    void shouldReturnClassifierNames() {
        assertThat(compositeClassifier.getClassifierNames()).containsExactlyInAnyOrder("address", "risk");
    }

    @Test
    void shouldReturnClassifierCount() {
        assertThat(compositeClassifier.getClassifierCount()).isEqualTo(2);
    }

    @Test
    void shouldCheckAnyPredicted() {
        String text = "test input";
        when(addressClassifier.classify(text)).thenReturn(createResult("valid", 0.9));
        when(riskClassifier.classify(text)).thenReturn(createResult("low", 0.85));

        CompositeClassificationResult result = compositeClassifier.classify(text);

        assertThat(result.anyPredicted("valid")).isTrue();
        assertThat(result.anyPredicted("low")).isTrue();
        assertThat(result.anyPredicted("unknown")).isFalse();
    }

    @Test
    void shouldThrowExceptionForEmptyClassifiers() {
        assertThatThrownBy(() -> new DefaultCompositeTextClassifier(Map.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one classifier is required");
    }

    @Test
    void shouldWarmupAllClassifiers() {
        List<String> warmupTexts = List.of("warmup1", "warmup2");

        when(addressClassifier.classifyBatch(warmupTexts)).thenReturn(List.of(
                createResult("valid", 0.9),
                createResult("valid", 0.8)
        ));
        when(riskClassifier.classifyBatch(warmupTexts)).thenReturn(List.of(
                createResult("low", 0.85),
                createResult("low", 0.75)
        ));

        compositeClassifier.warmup(warmupTexts);

        verify(addressClassifier).classifyBatch(warmupTexts);
        verify(riskClassifier).classifyBatch(warmupTexts);
    }

    @Test
    void shouldCloseOwnedClassifiers() {
        Map<String, TextClassifier> classifiers = new LinkedHashMap<>();
        classifiers.put("address", addressClassifier);
        DefaultCompositeTextClassifier ownedComposite = new DefaultCompositeTextClassifier(classifiers, true);

        ownedComposite.close();

        verify(addressClassifier).close();
    }

    private ClassificationResult createResult(String label, double confidence) {
        return ClassificationResult.builder()
                .predictedLabel(label)
                .confidence(confidence)
                .allProbabilities(List.of(
                        ClassificationResult.ClassProbability.builder()
                                .label(label)
                                .probability(confidence)
                                .build()
                ))
                .calibrated(false)
                .build();
    }
}
