package uk.codery.onnx.nlp.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Configuration and metadata for a classification model.
 */
@Value
@Builder
@lombok.extern.jackson.Jacksonized
public class ModelConfig {

    /**
     * Human-readable model name.
     */
    @NonNull
    String modelName;

    /**
     * Model version identifier.
     */
    @NonNull
    String version;

    /**
     * Ordered list of class labels.
     * Index in this list corresponds to the model's output index.
     */
    @NonNull
    List<String> classLabels;

    /**
     * Name of the input tensor (default: "input").
     */
    @NonNull
    @Builder.Default
    String inputTensorName = "input";

    /**
     * Name of the output tensor (default: "output").
     */
    @NonNull
    @Builder.Default
    String outputTensorName = "output";

    /**
     * Maximum sequence length for tokenized input.
     */
    @Builder.Default
    int maxSequenceLength = 512;

    /**
     * Vocabulary for tokenization, if using built-in tokenizer.
     */
    @Nullable
    Map<String, Integer> vocabulary;

    /**
     * Additional model-specific configuration.
     */
    @Nullable
    @Builder.Default
    Map<String, Object> metadata = Map.of();
}
