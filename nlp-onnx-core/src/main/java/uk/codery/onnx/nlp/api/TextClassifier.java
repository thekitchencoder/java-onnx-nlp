package uk.codery.onnx.nlp.api;

import lombok.NonNull;

import java.util.List;

/**
 * Core interface for text classification.
 * Implementations should be thread-safe.
 */
public interface TextClassifier extends AutoCloseable {

    /**
     * Classifies a single text input.
     *
     * @param text the input text to classify
     * @return the classification result
     */
    ClassificationResult classify(@NonNull String text);

    /**
     * Classifies multiple text inputs in a batch.
     * Batch processing is generally more efficient than individual classifications.
     *
     * @param texts the input texts to classify
     * @return the classification results in the same order as inputs
     */
    List<ClassificationResult> classifyBatch(@NonNull List<String> texts);

    /**
     * Warms up the model by running sample inferences.
     * This can improve performance for subsequent real classifications.
     *
     * @param sampleTexts sample texts to use for warmup
     */
    default void warmup(@NonNull List<String> sampleTexts) {
        classifyBatch(sampleTexts);
    }

    @Override
    void close();
}
