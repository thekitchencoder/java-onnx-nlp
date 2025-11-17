package uk.codery.onnx.nlp.harness;

import lombok.NonNull;
import uk.codery.onnx.nlp.tokenization.Tokenizer;

/**
 * A no-op tokenizer for models that accept string inputs directly (e.g., TF-IDF models).
 * This tokenizer doesn't actually tokenize - it's just a placeholder to satisfy the builder requirement.
 */
public class PassthroughTokenizer implements Tokenizer {

    @Override
    public long[] tokenize(@NonNull String text, int maxLength) {
        // For string-input ONNX models, tokenization is handled by the model itself
        // Return an empty array as this won't be used
        return new long[0];
    }
}
