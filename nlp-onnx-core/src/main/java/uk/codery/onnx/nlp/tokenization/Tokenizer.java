package uk.codery.onnx.nlp.tokenization;

import lombok.NonNull;

import java.util.List;

/**
 * Interface for tokenizing text into model input.
 */
public interface Tokenizer {

    /**
     * Tokenizes a single text into token IDs.
     *
     * @param text the input text
     * @param maxLength maximum sequence length
     * @return array of token IDs
     */
    long[] tokenize(@NonNull String text, int maxLength);

    /**
     * Tokenizes multiple texts into token IDs.
     *
     * @param texts the input texts
     * @param maxLength maximum sequence length
     * @return 2D array of token IDs [batch_size, sequence_length]
     */
    default long[][] tokenizeBatch(@NonNull List<String> texts, int maxLength) {
        long[][] result = new long[texts.size()][];
        for (int i = 0; i < texts.size(); i++) {
            result[i] = tokenize(texts.get(i), maxLength);
        }
        return result;
    }
}
