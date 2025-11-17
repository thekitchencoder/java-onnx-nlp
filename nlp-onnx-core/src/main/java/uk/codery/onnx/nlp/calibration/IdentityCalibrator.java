package uk.codery.onnx.nlp.calibration;

import lombok.NonNull;

/**
 * No-op calibrator that returns probabilities unchanged.
 */
public class IdentityCalibrator implements Calibrator {

    @Override
    public double[] calibrate(@NonNull double[] rawProbabilities) {
        return rawProbabilities;
    }

    @Override
    public boolean isIdentity() {
        return true;
    }
}
