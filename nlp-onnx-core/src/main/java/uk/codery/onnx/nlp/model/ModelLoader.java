package uk.codery.onnx.nlp.model;

import lombok.NonNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstraction for loading ONNX models and associated metadata.
 * Implementations can load from filesystem, classpath, remote storage, etc.
 */
public interface ModelLoader {

    /**
     * Loads a model bundle from the specified path.
     *
     * @param modelPath path to the model bundle (directory or archive)
     * @return the loaded model bundle
     * @throws IOException if loading fails
     */
    ModelBundle load(@NonNull Path modelPath) throws IOException;

    /**
     * Loads a model bundle from the specified resource name.
     * Resource loading is implementation-specific.
     *
     * @param resourceName name of the resource
     * @return the loaded model bundle
     * @throws IOException if loading fails
     */
    ModelBundle loadFromResource(@NonNull String resourceName) throws IOException;
}
