package uk.codery.onnx.nlp.api;

import lombok.NonNull;

import java.util.List;
import java.util.Set;

/**
 * A classifier that evaluates text against multiple models and combines the results.
 *
 * <p>This interface enables running multiple classifiers in parallel and
 * aggregating their results into a single composite result. Each underlying
 * classifier is identified by name.
 *
 * <p>Implementations should be thread-safe.
 */
public interface CompositeTextClassifier extends AutoCloseable {

    /**
     * Classifies a single text using all registered classifiers.
     *
     * @param text the input text to classify
     * @return composite result containing results from all classifiers
     */
    CompositeClassificationResult classify(@NonNull String text);

    /**
     * Classifies multiple texts using all registered classifiers.
     * Batch processing is generally more efficient than individual classifications.
     *
     * @param texts the input texts to classify
     * @return composite results in the same order as inputs
     */
    List<CompositeClassificationResult> classifyBatch(@NonNull List<String> texts);

    /**
     * Returns the names of all registered classifiers.
     *
     * @return set of classifier names
     */
    Set<String> getClassifierNames();

    /**
     * Returns the number of registered classifiers.
     *
     * @return the count of classifiers
     */
    int getClassifierCount();

    /**
     * Warms up all classifiers by running sample inferences.
     *
     * @param sampleTexts sample texts to use for warmup
     */
    default void warmup(@NonNull List<String> sampleTexts) {
        classifyBatch(sampleTexts);
    }

    @Override
    void close();
}
