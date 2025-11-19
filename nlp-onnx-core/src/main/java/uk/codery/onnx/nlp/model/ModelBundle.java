package uk.codery.onnx.nlp.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

/**
 * Contains the ONNX model and associated metadata needed for classification.
 */
@Value
@Builder
public class ModelBundle {

    /**
     * The raw ONNX model bytes.
     */
    // TODO this should be a supplier
    @NonNull
    byte[] modelBytes;

    /**
     * Model configuration and metadata.
     */
    @NonNull
    ModelConfig config;

    /**
     * Optional calibration data for post-processing scores.
     */
    @Nullable
    CalibrationData calibration;
}
