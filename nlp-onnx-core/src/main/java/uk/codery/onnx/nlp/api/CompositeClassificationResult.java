package uk.codery.onnx.nlp.api;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents the combined results of multiple text classifiers evaluating the same text.
 *
 * <p>Each classifier's result is stored in a map keyed by classifier name.
 * This enables evaluating text against multiple models in parallel and
 * combining the results.
 */
@Value
@Builder
public class CompositeClassificationResult {

    /**
     * The input text that was classified.
     */
    @NonNull
    String text;

    /**
     * Results from each classifier, keyed by classifier name.
     */
    @NonNull
    Map<String, ClassificationResult> results;

    /**
     * Returns the predicted labels from all classifiers.
     *
     * @return map of classifier name to predicted label
     */
    public Map<String, String> getPredictedLabels() {
        return results.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getPredictedLabel()
                ));
    }

    /**
     * Returns the confidence scores from all classifiers.
     *
     * @return map of classifier name to confidence score
     */
    public Map<String, Double> getConfidences() {
        return results.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getConfidence()
                ));
    }

    /**
     * Returns the result from a specific classifier.
     *
     * @param classifierName the name of the classifier
     * @return the classification result, or null if not found
     */
    public ClassificationResult getResult(String classifierName) {
        return results.get(classifierName);
    }

    /**
     * Checks if any classifier predicted a specific label.
     *
     * @param label the label to check for
     * @return true if any classifier predicted this label
     */
    public boolean anyPredicted(String label) {
        return results.values().stream()
                .anyMatch(r -> r.getPredictedLabel().equals(label));
    }

    /**
     * Returns the number of classifiers that contributed results.
     *
     * @return the count of classifier results
     */
    public int getClassifierCount() {
        return results.size();
    }
}
