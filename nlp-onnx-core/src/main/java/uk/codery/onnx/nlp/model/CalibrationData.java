package uk.codery.onnx.nlp.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Map;

/**
 * Calibration data for post-processing model outputs.
 * Supports various calibration methods like temperature scaling, Platt scaling, etc.
 */
@Value
@Builder
@lombok.extern.jackson.Jacksonized
public class CalibrationData {

    /**
     * Type of calibration (e.g., "temperature", "platt", "isotonic").
     */
    @NonNull
    String calibrationType;

    /**
     * Calibration parameters specific to the calibration type.
     * For temperature scaling: {"temperature": 1.5}
     * For Platt scaling: {"a": 2.0, "b": -1.0}
     */
    @NonNull
    Map<String, Double> parameters;
}
