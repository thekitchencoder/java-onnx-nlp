package uk.codery.onnx.nlp.calibration;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Platt scaling calibrator for binary classification.
 * Applies sigmoid(a * x + b) transformation.
 */
@RequiredArgsConstructor
public class PlattCalibrator implements Calibrator {

    private final double a;
    private final double b;

    @Override
    public double[] calibrate(@NonNull double[] rawProbabilities) {
        if (rawProbabilities.length != 2) {
            throw new IllegalArgumentException(
                    "Platt scaling only supports binary classification, got " +
                    rawProbabilities.length + " classes"
            );
        }

        // Apply Platt scaling to positive class probability
        double posProb = rawProbabilities[1];
        double logit = Math.log(posProb / (1.0 - posProb + 1e-10));
        double calibratedProb = 1.0 / (1.0 + Math.exp(-(a * logit + b)));

        return new double[]{1.0 - calibratedProb, calibratedProb};
    }
}
