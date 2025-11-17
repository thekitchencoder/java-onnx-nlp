package uk.codery.onnx.nlp.api;

import lombok.NonNull;

import java.util.List;

/**
 * Interface for text preprocessing before classification.
 */
public interface TextPreprocessor {

    /**
     * Preprocesses a single text input.
     *
     * @param text the raw input text
     * @return the preprocessed text
     */
    String preprocess(@NonNull String text);

    /**
     * Preprocesses multiple text inputs.
     *
     * @param texts the raw input texts
     * @return the preprocessed texts in the same order
     */
    default List<String> preprocessBatch(@NonNull List<String> texts) {
        return texts.stream()
                .map(this::preprocess)
                .toList();
    }
}
