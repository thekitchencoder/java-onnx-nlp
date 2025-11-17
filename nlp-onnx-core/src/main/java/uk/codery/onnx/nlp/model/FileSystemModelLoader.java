package uk.codery.onnx.nlp.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Default model loader that loads from the filesystem.
 * Expects a model directory with:
 * - model.onnx (the ONNX model file)
 * - config.json (model configuration)
 * - calibration.json (optional calibration data)
 */
@Slf4j
public class FileSystemModelLoader implements ModelLoader {

    private static final String MODEL_FILE = "model.onnx";
    private static final String CONFIG_FILE = "config.json";
    private static final String CALIBRATION_FILE = "calibration.json";

    private final ObjectMapper objectMapper;

    public FileSystemModelLoader() {
        this.objectMapper = new ObjectMapper();
    }

    public FileSystemModelLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ModelBundle load(@NonNull Path modelPath) throws IOException {
        if (!Files.isDirectory(modelPath)) {
            throw new IOException("Model path must be a directory: " + modelPath);
        }

        log.info("Loading model from: {}", modelPath);

        // Load ONNX model
        Path modelFile = modelPath.resolve(MODEL_FILE);
        if (!Files.exists(modelFile)) {
            throw new IOException("Model file not found: " + modelFile);
        }
        byte[] modelBytes = Files.readAllBytes(modelFile);
        log.debug("Loaded model file: {} bytes", modelBytes.length);

        // Load config
        Path configFile = modelPath.resolve(CONFIG_FILE);
        if (!Files.exists(configFile)) {
            throw new IOException("Config file not found: " + configFile);
        }
        ModelConfig config = objectMapper.readValue(configFile.toFile(), ModelConfig.class);
        log.debug("Loaded config: {}", config.getModelName());

        // Load optional calibration
        CalibrationData calibration = null;
        Path calibrationFile = modelPath.resolve(CALIBRATION_FILE);
        if (Files.exists(calibrationFile)) {
            calibration = objectMapper.readValue(calibrationFile.toFile(), CalibrationData.class);
            log.debug("Loaded calibration: {}", calibration.getCalibrationType());
        }

        return ModelBundle.builder()
                .modelBytes(modelBytes)
                .config(config)
                .calibration(calibration)
                .build();
    }

    @Override
    public ModelBundle loadFromResource(@NonNull String resourceName) throws IOException {
        throw new UnsupportedOperationException(
                "Resource loading not implemented in FileSystemModelLoader. " +
                "Use a custom ModelLoader implementation for resource loading."
        );
    }
}
