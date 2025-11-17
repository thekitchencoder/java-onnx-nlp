package uk.codery.onnx.nlp.api;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.List;

/**
 * Represents the result of a text classification.
 */
@Value
@Builder
public class ClassificationResult {

    /**
     * The predicted class label.
     */
    @NonNull
    String predictedLabel;

    /**
     * Confidence score for the predicted label (0.0 to 1.0).
     */
    double confidence;

    /**
     * All class probabilities, ordered by the model's class index.
     */
    @NonNull
    List<ClassProbability> allProbabilities;

    /**
     * Whether calibration was applied to the scores.
     */
    boolean calibrated;

    /**
     * Represents a single class probability.
     */
    @Value
    @Builder
    public static class ClassProbability {
        @NonNull
        String label;
        double probability;
    }
}
