package uk.codery.onnx.nlp.preprocessing;

import lombok.NonNull;
import uk.codery.onnx.nlp.api.TextPreprocessor;

/**
 * No-op preprocessor that returns text unchanged.
 */
public class NoOpPreprocessor implements TextPreprocessor {

    @Override
    public String preprocess(@NonNull String text) {
        return text;
    }
}
