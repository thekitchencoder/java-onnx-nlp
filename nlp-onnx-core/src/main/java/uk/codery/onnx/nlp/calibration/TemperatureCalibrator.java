package uk.codery.onnx.nlp.calibration;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Temperature scaling calibrator.
 * Divides logits by temperature before applying softmax.
 */
@RequiredArgsConstructor
public class TemperatureCalibrator implements Calibrator {

    private final double temperature;

    @Override
    public double[] calibrate(@NonNull double[] rawProbabilities) {
        if (temperature == 1.0) {
            return rawProbabilities;
        }

        // Convert probabilities back to logits, apply temperature, then softmax
        double[] logits = new double[rawProbabilities.length];
        for (int i = 0; i < rawProbabilities.length; i++) {
            logits[i] = Math.log(Math.max(rawProbabilities[i], 1e-10));
        }

        // Apply temperature scaling
        for (int i = 0; i < logits.length; i++) {
            logits[i] /= temperature;
        }

        // Apply softmax
        return softmax(logits);
    }

    private double[] softmax(double[] logits) {
        double max = Double.NEGATIVE_INFINITY;
        for (double logit : logits) {
            max = Math.max(max, logit);
        }

        double sum = 0.0;
        double[] result = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            result[i] = Math.exp(logits[i] - max);
            sum += result[i];
        }

        for (int i = 0; i < result.length; i++) {
            result[i] /= sum;
        }

        return result;
    }
}
