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
import java.util.stream.Collectors;

/**
 * Default implementation of CompositeTextClassifier that runs multiple classifiers in parallel.
 *
 * <p>This implementation uses parallel streams to evaluate text against multiple classifiers
 * concurrently, combining the results into a single {@link CompositeClassificationResult}.
 *
 * <p>Thread safety: This class is thread-safe for concurrent classification calls.
 */
@Slf4j
public class DefaultCompositeTextClassifier implements CompositeTextClassifier {

    private record Tuple<T>(String key, T value) {}

    private final Map<String, TextClassifier> classifiers;
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
        log.info("Created composite classifier with {} classifiers: {}",
                classifiers.size(), classifiers.keySet());
    }

    @Override
    public CompositeClassificationResult classify(@NonNull String text) {
        Map<String, ClassificationResult> results = classifiers.entrySet()
                .parallelStream()
                .map(entry -> new Tuple<>(entry.getKey(), entry.getValue().classify(text)))
                .collect(Collectors.toMap(
                        Tuple::key,
                        Tuple::value,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

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

        // Run each classifier's batch in parallel
        Map<String, List<ClassificationResult>> allResults = classifiers.entrySet()
                .parallelStream()
                .map(entry -> new Tuple<>(entry.getKey(), entry.getValue().classifyBatch(texts)))
                .collect(Collectors.toMap(
                        Tuple::key,
                        Tuple::value,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        return transposeResults(texts, allResults);
    }

    /**
     * Transposes results from classifier-keyed map to text-indexed list.
     */
    private List<CompositeClassificationResult> transposeResults(
            List<String> texts,
            Map<String, List<ClassificationResult>> resultsByClassifier
    ) {
        // Validate all classifiers returned the expected number of results
        for (Map.Entry<String, List<ClassificationResult>> entry : resultsByClassifier.entrySet()) {
            if (entry.getValue().size() != texts.size()) {
                throw new IllegalStateException(String.format(
                        "Classifier '%s' returned %d results but expected %d",
                        entry.getKey(), entry.getValue().size(), texts.size()
                ));
            }
        }

        List<CompositeClassificationResult> compositeResults = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            Map<String, ClassificationResult> resultsForText = new LinkedHashMap<>();

            for (Map.Entry<String, List<ClassificationResult>> entry : resultsByClassifier.entrySet()) {
                resultsForText.put(entry.getKey(), entry.getValue().get(i));
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
