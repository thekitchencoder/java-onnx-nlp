package uk.codery.onnx.nlp.impl;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import uk.codery.onnx.nlp.api.ClassificationResult;
import uk.codery.onnx.nlp.api.CompositeClassificationResult;
import uk.codery.onnx.nlp.api.CompositeTextClassifier;
import uk.codery.onnx.nlp.api.TextClassifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of CompositeTextClassifier that runs multiple classifiers in parallel.
 *
 * <p>This implementation uses a thread pool to evaluate text against multiple classifiers
 * concurrently, combining the results into a single {@link CompositeClassificationResult}.
 *
 * <p>Thread safety: This class is thread-safe for concurrent classification calls.
 */
@Slf4j
public class DefaultCompositeTextClassifier implements CompositeTextClassifier {

    private final Map<String, TextClassifier> classifiers;
    private final ExecutorService executor;
    private final boolean ownsClassifiers;

    /**
     * Creates a composite classifier with the given classifiers.
     *
     * @param classifiers map of classifier name to classifier instance
     * @param ownsClassifiers whether this composite should close the classifiers on shutdown
     */
    public DefaultCompositeTextClassifier(
            @NonNull Map<String, TextClassifier> classifiers,
            boolean ownsClassifiers
    ) {
        if (classifiers.isEmpty()) {
            throw new IllegalArgumentException("At least one classifier is required");
        }
        // Use LinkedHashMap to preserve insertion order
        this.classifiers = new LinkedHashMap<>(classifiers);
        this.ownsClassifiers = ownsClassifiers;
        this.executor = Executors.newFixedThreadPool(
                Math.min(classifiers.size(), Runtime.getRuntime().availableProcessors())
        );
        log.info("Created composite classifier with {} classifiers: {}",
                classifiers.size(), classifiers.keySet());
    }

    /**
     * Creates a composite classifier with the given classifiers using a custom executor.
     *
     * @param classifiers map of classifier name to classifier instance
     * @param executor executor service for parallel execution
     * @param ownsClassifiers whether this composite should close the classifiers on shutdown
     */
    public DefaultCompositeTextClassifier(
            @NonNull Map<String, TextClassifier> classifiers,
            @NonNull ExecutorService executor,
            boolean ownsClassifiers
    ) {
        if (classifiers.isEmpty()) {
            throw new IllegalArgumentException("At least one classifier is required");
        }
        this.classifiers = new LinkedHashMap<>(classifiers);
        this.executor = executor;
        this.ownsClassifiers = ownsClassifiers;
        log.info("Created composite classifier with {} classifiers: {}",
                classifiers.size(), classifiers.keySet());
    }

    @Override
    public CompositeClassificationResult classify(@NonNull String text) {
        List<CompletableFuture<Map.Entry<String, ClassificationResult>>> futures = new ArrayList<>();

        for (Map.Entry<String, TextClassifier> entry : classifiers.entrySet()) {
            String name = entry.getKey();
            TextClassifier classifier = entry.getValue();

            CompletableFuture<Map.Entry<String, ClassificationResult>> future =
                    CompletableFuture.supplyAsync(() -> {
                        ClassificationResult result = classifier.classify(text);
                        return Map.entry(name, result);
                    }, executor);

            futures.add(future);
        }

        // Wait for all classifications to complete
        Map<String, ClassificationResult> results = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<String, ClassificationResult>> future : futures) {
            try {
                Map.Entry<String, ClassificationResult> entry = future.join();
                results.put(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.error("Classification failed", e);
                throw new RuntimeException("Composite classification failed", e);
            }
        }

        return CompositeClassificationResult.builder()
                .text(text)
                .results(results)
                .build();
    }

    @Override
    public List<CompositeClassificationResult> classifyBatch(@NonNull List<String> texts) {
        if (texts.isEmpty()) {
            return Collections.emptyList();
        }

        // For batch processing, we run each classifier's batch in parallel
        List<CompletableFuture<Map.Entry<String, List<ClassificationResult>>>> futures = new ArrayList<>();

        for (Map.Entry<String, TextClassifier> entry : classifiers.entrySet()) {
            String name = entry.getKey();
            TextClassifier classifier = entry.getValue();

            CompletableFuture<Map.Entry<String, List<ClassificationResult>>> future =
                    CompletableFuture.supplyAsync(() -> {
                        List<ClassificationResult> batchResults = classifier.classifyBatch(texts);
                        return Map.entry(name, batchResults);
                    }, executor);

            futures.add(future);
        }

        // Collect results from all classifiers
        Map<String, List<ClassificationResult>> allResults = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<String, List<ClassificationResult>>> future : futures) {
            try {
                Map.Entry<String, List<ClassificationResult>> entry = future.join();
                allResults.put(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.error("Batch classification failed", e);
                throw new RuntimeException("Composite batch classification failed", e);
            }
        }

        // Transpose results: from classifier -> list to list -> classifier map
        List<CompositeClassificationResult> compositeResults = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            Map<String, ClassificationResult> resultsForText = new LinkedHashMap<>();

            for (Map.Entry<String, List<ClassificationResult>> entry : allResults.entrySet()) {
                String classifierName = entry.getKey();
                ClassificationResult result = entry.getValue().get(i);
                resultsForText.put(classifierName, result);
            }

            compositeResults.add(CompositeClassificationResult.builder()
                    .text(text)
                    .results(resultsForText)
                    .build());
        }

        return compositeResults;
    }

    @Override
    public Set<String> getClassifierNames() {
        return Collections.unmodifiableSet(classifiers.keySet());
    }

    @Override
    public int getClassifierCount() {
        return classifiers.size();
    }

    @Override
    public void close() {
        log.info("Shutting down composite classifier");

        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate in time, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for executor shutdown", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (ownsClassifiers) {
            for (Map.Entry<String, TextClassifier> entry : classifiers.entrySet()) {
                try {
                    entry.getValue().close();
                } catch (Exception e) {
                    log.warn("Failed to close classifier '{}'", entry.getKey(), e);
                }
            }
        }
    }
}
