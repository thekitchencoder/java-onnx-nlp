package examples;

import uk.codery.onnx.nlp.TextClassifierBuilder;
import uk.codery.onnx.nlp.api.ClassificationResult;
import uk.codery.onnx.nlp.api.TextClassifier;
import uk.codery.onnx.nlp.model.FileSystemModelLoader;
import uk.codery.onnx.nlp.preprocessing.BasicTextPreprocessor;

import java.nio.file.Path;
import java.util.List;

/**
 * Example usage of the core NLP ONNX library.
 */
public class CoreUsageExample {

    public static void main(String[] args) throws Exception {
        // Path to your model directory
        Path modelPath = Path.of("/path/to/your/model");

        // Create classifier
        try (TextClassifier classifier = TextClassifierBuilder.newBuilder()
                .modelLoader(new FileSystemModelLoader())
                .modelPath(modelPath)
                .preprocessor(BasicTextPreprocessor.createDefault())
                .build()) {

            // Optional: warmup the model
            classifier.warmup(List.of(
                    "This is a warmup text",
                    "Another warmup example"
            ));

            // Single classification
            ClassificationResult result = classifier.classify("This is a great product!");

            System.out.println("Predicted Label: " + result.getPredictedLabel());
            System.out.println("Confidence: " + String.format("%.2f", result.getConfidence()));
            System.out.println("Calibrated: " + result.isCalibrated());

            System.out.println("\nAll probabilities:");
            for (ClassificationResult.ClassProbability prob : result.getAllProbabilities()) {
                System.out.printf("  %s: %.4f%n", prob.getLabel(), prob.getProbability());
            }

            // Batch classification
            List<String> texts = List.of(
                    "I love this!",
                    "This is terrible",
                    "It's okay, nothing special"
            );

            List<ClassificationResult> results = classifier.classifyBatch(texts);

            System.out.println("\nBatch results:");
            for (int i = 0; i < texts.size(); i++) {
                System.out.printf("%s -> %s (%.2f)%n",
                        texts.get(i),
                        results.get(i).getPredictedLabel(),
                        results.get(i).getConfidence()
                );
            }
        }
    }
}
