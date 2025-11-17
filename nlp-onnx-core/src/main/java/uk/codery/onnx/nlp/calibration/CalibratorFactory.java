package uk.codery.onnx.nlp.calibration;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;
import uk.codery.onnx.nlp.model.CalibrationData;

/**
 * Factory for creating calibrators from calibration data.
 */
@UtilityClass
public class CalibratorFactory {

    /**
     * Creates a calibrator from calibration data.
     * Returns an identity calibrator if data is null.
     *
     * @param calibrationData the calibration data, or null
     * @return the appropriate calibrator
     */
    public static Calibrator create(@Nullable CalibrationData calibrationData) {
        if (calibrationData == null) {
            return new IdentityCalibrator();
        }

        return switch (calibrationData.getCalibrationType().toLowerCase()) {
            case "temperature" -> new TemperatureCalibrator(
                    calibrationData.getParameters().getOrDefault("temperature", 1.0)
            );
            case "platt" -> new PlattCalibrator(
                    calibrationData.getParameters().getOrDefault("a", 1.0),
                    calibrationData.getParameters().getOrDefault("b", 0.0)
            );
            default -> throw new IllegalArgumentException(
                    "Unsupported calibration type: " + calibrationData.getCalibrationType()
            );
        };
    }
}
