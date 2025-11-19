package uk.codery.onnx.nlp.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeClassificationResultTest {

    @Test
    void shouldStorePredictedLabels() {
        CompositeClassificationResult result = createResult();

        Map<String, String> labels = result.getPredictedLabels();

        assertThat(labels).containsEntry("address", "valid");
        assertThat(labels).containsEntry("risk", "low");
    }

    @Test
    void shouldStoreConfidences() {
        CompositeClassificationResult result = createResult();

        Map<String, Double> confidences = result.getConfidences();

        assertThat(confidences.get("address")).isEqualTo(0.9);
        assertThat(confidences.get("risk")).isEqualTo(0.85);
    }

    @Test
    void shouldGetResultByName() {
        CompositeClassificationResult result = createResult();

        ClassificationResult addressResult = result.getResult("address");

        assertThat(addressResult).isNotNull();
        assertThat(addressResult.getPredictedLabel()).isEqualTo("valid");
    }

    @Test
    void shouldReturnNullForUnknownClassifier() {
        CompositeClassificationResult result = createResult();

        assertThat(result.getResult("unknown")).isNull();
    }

    @Test
    void shouldCheckAnyPredicted() {
        CompositeClassificationResult result = createResult();

        assertThat(result.anyPredicted("valid")).isTrue();
        assertThat(result.anyPredicted("low")).isTrue();
        assertThat(result.anyPredicted("high")).isFalse();
    }

    @Test
    void shouldReturnClassifierCount() {
        CompositeClassificationResult result = createResult();

        assertThat(result.getClassifierCount()).isEqualTo(2);
    }

    @Test
    void shouldStoreOriginalText() {
        CompositeClassificationResult result = createResult();

        assertThat(result.getText()).isEqualTo("test input");
    }

    private CompositeClassificationResult createResult() {
        ClassificationResult addressResult = ClassificationResult.builder()
                .predictedLabel("valid")
                .confidence(0.9)
                .allProbabilities(List.of(
                        ClassificationResult.ClassProbability.builder()
                                .label("valid")
                                .probability(0.9)
                                .build(),
                        ClassificationResult.ClassProbability.builder()
                                .label("invalid")
                                .probability(0.1)
                                .build()
                ))
                .calibrated(false)
                .build();

        ClassificationResult riskResult = ClassificationResult.builder()
                .predictedLabel("low")
                .confidence(0.85)
                .allProbabilities(List.of(
                        ClassificationResult.ClassProbability.builder()
                                .label("low")
                                .probability(0.85)
                                .build(),
                        ClassificationResult.ClassProbability.builder()
                                .label("high")
                                .probability(0.15)
                                .build()
                ))
                .calibrated(false)
                .build();

        return CompositeClassificationResult.builder()
                .text("test input")
                .results(Map.of(
                        "address", addressResult,
                        "risk", riskResult
                ))
                .build();
    }
}
