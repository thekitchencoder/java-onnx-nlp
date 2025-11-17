package uk.codery.onnx.nlp.calibration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TemperatureCalibratorTest {

    @Test
    void shouldReturnUnchangedWhenTemperatureIsOne() {
        TemperatureCalibrator calibrator = new TemperatureCalibrator(1.0);
        double[] probabilities = {0.2, 0.5, 0.3};

        double[] result = calibrator.calibrate(probabilities);

        assertThat(result).containsExactly(probabilities, within(0.01));
    }

    @Test
    void shouldSmoothProbabilitiesWithHighTemperature() {
        TemperatureCalibrator calibrator = new TemperatureCalibrator(2.0);
        double[] probabilities = {0.1, 0.8, 0.1};

        double[] result = calibrator.calibrate(probabilities);

        // Higher temperature should make probabilities more uniform
        assertThat(result[1]).isLessThan(probabilities[1]);
        assertThat(result[0]).isGreaterThan(probabilities[0]);
    }

    @Test
    void shouldMakeProbabilitiesMorePeakedWithLowTemperature() {
        TemperatureCalibrator calibrator = new TemperatureCalibrator(0.5);
        double[] probabilities = {0.1, 0.8, 0.1};

        double[] result = calibrator.calibrate(probabilities);

        // Lower temperature should make the peak more pronounced
        assertThat(result[1]).isGreaterThan(probabilities[1]);
    }

    @Test
    void shouldSumToOne() {
        TemperatureCalibrator calibrator = new TemperatureCalibrator(1.5);
        double[] probabilities = {0.2, 0.5, 0.3};

        double[] result = calibrator.calibrate(probabilities);

        double sum = 0.0;
        for (double p : result) {
            sum += p;
        }
        assertThat(sum).isCloseTo(1.0, within(0.0001));
    }
}
