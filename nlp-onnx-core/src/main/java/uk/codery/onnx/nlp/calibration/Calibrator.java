package uk.codery.onnx.nlp.calibration;

import lombok.NonNull;

/**
 * Interface for calibrating model output probabilities.
 */
public interface Calibrator {

    /**
     * Calibrates a probability distribution.
     *
     * @param rawProbabilities the raw probabilities from the model
     * @return the calibrated probabilities (must sum to 1.0)
     */
    double[] calibrate(@NonNull double[] rawProbabilities);

    /**
     * Returns whether this calibrator is a no-op (identity transformation).
     */
    default boolean isIdentity() {
        return false;
    }
}
