package uk.codery.onnx.nlp.harness;

import ai.onnxruntime.OrtEnvironment;
import uk.codery.onnx.nlp.TextClassifierBuilder;
import uk.codery.onnx.nlp.api.ClassificationResult;
import uk.codery.onnx.nlp.api.TextClassifier;
import uk.codery.onnx.nlp.model.FileSystemModelLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Standalone test harness for exercising the ONNX NLP classifiers.
 * Loads all three models (address, voda, risk) and runs them against test data.
 */
public class ModelTestHarness {

    private static final String SCRIPTS_DIR = "scripts";
    private static final String MODELS_DIR = SCRIPTS_DIR + "/models";
    private static final String TEST_DATA = SCRIPTS_DIR + "/example/test_data.txt";

    public static void main(String[] args) throws Exception {
        System.out.println("=== ONNX NLP Classifier Test Harness ===\n");

        // Determine project root (go up from current directory to find scripts folder)
        Path projectRoot = findProjectRoot();
        if (projectRoot == null) {
            System.err.println("Could not find project root. Make sure to run from the project directory.");
            System.exit(1);
        }

        System.out.println("Project root: " + projectRoot);
        System.out.println();

        // Load test data
        Path testDataPath = projectRoot.resolve(TEST_DATA);
        if (!Files.exists(testDataPath)) {
            System.err.println("Test data not found: " + testDataPath);
            System.exit(1);
        }

        List<String> testTexts = Files.readAllLines(testDataPath);
        System.out.println("Loaded " + testTexts.size() + " test samples\n");

        // Create shared ONNX environment
        try (OrtEnvironment env = OrtEnvironment.getEnvironment()) {

            // Load all three classifiers
            TextClassifier addressClassifier = loadClassifier(projectRoot, "address", env);
            TextClassifier vodaClassifier = loadClassifier(projectRoot, "voda", env);
            TextClassifier riskClassifier = loadClassifier(projectRoot, "risk", env);

            System.out.println("=== Running Classifications ===\n");

            // Classify each test sample with all three models
            for (int i = 0; i < testTexts.size(); i++) {
                String text = testTexts.get(i);
                System.out.println("Sample " + (i + 1) + ": \"" + text + "\"");
                System.out.println("-".repeat(80));

                // Address classification
                ClassificationResult addressResult = addressClassifier.classify(text);
                printResult("ADDRESS", addressResult);

                // VODA classification
                ClassificationResult vodaResult = vodaClassifier.classify(text);
                printResult("VODA   ", vodaResult);

                // Risk classification
                ClassificationResult riskResult = riskClassifier.classify(text);
                printResult("RISK   ", riskResult);

                System.out.println();
            }

            // Clean up classifiers
            addressClassifier.close();
            vodaClassifier.close();
            riskClassifier.close();

            System.out.println("=== Test Harness Complete ===");
        }
    }

    private static TextClassifier loadClassifier(Path projectRoot, String modelName, OrtEnvironment env) throws Exception {
        Path modelPath = projectRoot.resolve(MODELS_DIR).resolve(modelName);

        System.out.println("Loading " + modelName + " classifier from: " + modelPath);

        TextClassifier classifier = TextClassifierBuilder.newBuilder()
                .modelLoader(new FileSystemModelLoader())
                .modelPath(modelPath)
                .environment(env)
                .build();

        System.out.println("  ✓ " + modelName + " classifier loaded successfully");

        return classifier;
    }

    private static void printResult(String label, ClassificationResult result) {
        String predictedClass = result.getPredictedLabel();
        double confidence = result.getConfidence();

        // For binary classifiers, show both class probabilities
        System.out.printf("  %s: %s (%.2f%%) [",
            label,
            predictedClass,
            confidence * 100);

        // Print all class probabilities
        result.getAllProbabilities().forEach(cp ->
            System.out.printf("%s: %.4f  ", cp.getLabel(), cp.getProbability())
        );
        System.out.println("]");
    }

    private static Path findProjectRoot() {
        Path current = Paths.get("").toAbsolutePath();

        // Try current directory first
        if (Files.exists(current.resolve(SCRIPTS_DIR))) {
            return current;
        }

        // Try parent directories (up to 3 levels)
        Path parent = current.getParent();
        for (int i = 0; i < 3 && parent != null; i++) {
            if (Files.exists(parent.resolve(SCRIPTS_DIR))) {
                return parent;
            }
            parent = parent.getParent();
        }

        return null;
    }
}
