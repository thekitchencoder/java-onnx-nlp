/*
 * QuickStart.java - Getting started with nlp-onnx-core
 *
 * This is a standalone example demonstrating basic usage of the library.
 * Copy this file to your project, add the dependencies, and run!
 *
 * PREREQUISITES:
 * 1. Add nlp-onnx-core dependency to your project
 * 2. Have a model directory with:
 *    - model.onnx (your ONNX model)
 *    - config.json (model configuration)
 *    - calibration.json (optional)
 *
 * Example config.json:
 * {
 *   "modelName": "sentiment-classifier",
 *   "version": "1.0.0",
 *   "classLabels": ["negative", "neutral", "positive"],
 *   "vocabulary": { "[PAD]": 0, "[UNK]": 1, "good": 2, "bad": 3, ... }
 * }
 */

// Package declaration - adjust to your project structure
// package com.example.demo;

import uk.codery.onnx.nlp.TextClassifierBuilder;
import uk.codery.onnx.nlp.api.ClassificationResult;
import uk.codery.onnx.nlp.api.TextClassifier;
import uk.codery.onnx.nlp.model.FileSystemModelLoader;
import uk.codery.onnx.nlp.preprocessing.BasicTextPreprocessor;

import java.nio.file.Path;
import java.util.List;

public class QuickStart {

    public static void main(String[] args) {
        // Path to your model directory
        Path modelPath = Path.of("path/to/your/model");

        // Create classifier using the builder pattern
        // try-with-resources ensures proper cleanup of ONNX resources
        try (TextClassifier classifier = TextClassifierBuilder.newBuilder()
                .modelLoader(new FileSystemModelLoader())
                .modelPath(modelPath)
                .preprocessor(BasicTextPreprocessor.createDefault())
                .build()) {

            // --- Single Text Classification ---
            System.out.println("=== Single Classification ===");

            ClassificationResult result = classifier.classify("This product is amazing!");

            System.out.println("Text: \"This product is amazing!\"");
            System.out.println("Predicted: " + result.getPredictedLabel());
            System.out.printf("Confidence: %.2f%%%n", result.getConfidence() * 100);
            System.out.println("Calibrated: " + result.isCalibrated());

            // Access all class probabilities
            System.out.println("\nAll probabilities:");
            result.getAllProbabilities().forEach(prob ->
                System.out.printf("  %s: %.4f%n", prob.getLabel(), prob.getProbability())
            );

            // --- Batch Classification ---
            System.out.println("\n=== Batch Classification ===");

            List<String> texts = List.of(
                "I love this!",
                "This is okay.",
                "Terrible experience, would not recommend."
            );

            List<ClassificationResult> results = classifier.classifyBatch(texts);

            for (int i = 0; i < texts.size(); i++) {
                ClassificationResult r = results.get(i);
                System.out.printf("\"%s\"%n  -> %s (%.1f%%)%n",
                    texts.get(i),
                    r.getPredictedLabel(),
                    r.getConfidence() * 100
                );
            }

            // --- Optional: Model Warmup ---
            // Uncomment to warm up the model before production use
            // classifier.warmup(List.of("sample text for warmup"));

        } catch (Exception e) {
            System.err.println("Error during classification: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
