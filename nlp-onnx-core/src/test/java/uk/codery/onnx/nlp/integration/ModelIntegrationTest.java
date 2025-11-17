package uk.codery.onnx.nlp.integration;

import ai.onnxruntime.OrtEnvironment;
import org.junit.jupiter.api.*;
import uk.codery.onnx.nlp.TextClassifierBuilder;
import uk.codery.onnx.nlp.api.ClassificationResult;
import uk.codery.onnx.nlp.api.TextClassifier;
import uk.codery.onnx.nlp.model.FileSystemModelLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration tests for ONNX NLP classifiers using real models from the scripts folder.
 * These tests verify that models can be loaded and produce reasonable predictions.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModelIntegrationTest {

    private static final String SCRIPTS_DIR = "scripts/models";

    private OrtEnvironment ortEnvironment;
    private TextClassifier addressClassifier;
    private TextClassifier vodaClassifier;
    private TextClassifier riskClassifier;
    private Path projectRoot;

    @BeforeAll
    void setUp() throws Exception {
        // Find project root
        projectRoot = findProjectRoot();
        assertThat(projectRoot).isNotNull();

        // Create shared ONNX environment
        ortEnvironment = OrtEnvironment.getEnvironment();

        // Load all three classifiers
        addressClassifier = loadClassifier("address");
        vodaClassifier = loadClassifier("voda");
        riskClassifier = loadClassifier("risk");
    }

    @AfterAll
    void tearDown() throws Exception {
        if (addressClassifier != null) addressClassifier.close();
        if (vodaClassifier != null) vodaClassifier.close();
        if (riskClassifier != null) riskClassifier.close();
    }

    @Test
    void shouldLoadAddressClassifier() {
        assertThat(addressClassifier).isNotNull();
    }

    @Test
    void shouldLoadVodaClassifier() {
        assertThat(vodaClassifier).isNotNull();
    }

    @Test
    void shouldLoadRiskClassifier() {
        assertThat(riskClassifier).isNotNull();
    }

    @Test
    void shouldDetectAddress() {
        String textWithAddress = "Flat 9,Dodd canyon,South Debra,B42 3YU";
        ClassificationResult result = addressClassifier.classify(textWithAddress);

        assertThat(result).isNotNull();
        assertThat(result.getPredictedLabel()).isEqualTo("has_address");
        assertThat(result.getConfidence()).isGreaterThan(0.5);
    }

    @Test
    void shouldDetectNoAddress() {
        String textWithoutAddress = "I am in a tent and it's not safe, please help.";
        ClassificationResult result = addressClassifier.classify(textWithoutAddress);

        assertThat(result).isNotNull();
        assertThat(result.getPredictedLabel()).isEqualTo("no_address");
        assertThat(result.getConfidence()).isGreaterThan(0.5);
    }

    @Test
    void shouldDetectVoda() {
        String textWithVoda = "Domestic Violence";
        ClassificationResult result = vodaClassifier.classify(textWithVoda);
System.out.println(result);
        assertThat(result).isNotNull();
        assertThat(result.getPredictedLabel()).isEqualTo("has_voda");
        assertThat(result.getConfidence()).isGreaterThan(0.5);
    }

    @Test
    void shouldDetectNoVoda() {
        String textWithoutVoda = "Flat 9,Dodd canyon,South Debra,B42 3YU";
        ClassificationResult result = vodaClassifier.classify(textWithoutVoda);

        assertThat(result).isNotNull();
        assertThat(result.getPredictedLabel()).isEqualTo("no_voda");
        assertThat(result.getConfidence()).isGreaterThan(0.5);
    }

    @Test
    void shouldDetectRisk() {
        String textWithRisk = "Help, I'm in danger";
        ClassificationResult result = riskClassifier.classify(textWithRisk);

        assertThat(result).isNotNull();
        assertThat(result.getPredictedLabel()).isEqualTo("has_risk");
        assertThat(result.getConfidence()).isGreaterThan(0.5);
    }

    @Test
    void shouldDetectNoRisk() {
        String textWithoutRisk = "Account review due";
        ClassificationResult result = riskClassifier.classify(textWithoutRisk);

        assertThat(result).isNotNull();
        assertThat(result.getPredictedLabel()).isEqualTo("no_risk");
        assertThat(result.getConfidence()).isGreaterThan(0.5);
    }

    @Test
    void shouldReturnAllClassProbabilities() {
        String text = "Help available 24/7";
        ClassificationResult result = addressClassifier.classify(text);

        assertThat(result.getAllProbabilities()).hasSize(2);

        // Check that both class labels are present
        List<String> labels = result.getAllProbabilities().stream()
                .map(ClassificationResult.ClassProbability::getLabel)
                .toList();
        assertThat(labels).containsExactlyInAnyOrder("no_address", "has_address");

        // Probabilities should sum to approximately 1.0
        double sum = result.getAllProbabilities().stream()
                .mapToDouble(ClassificationResult.ClassProbability::getProbability)
                .sum();
        assertThat(sum).isCloseTo(1.0, within(0.01));
    }

    @Test
    void shouldApplyCalibration() {
        String text = "Flat 52d,, Fort town,G03 9WZ";
        ClassificationResult result = addressClassifier.classify(text);

        // Verify that calibration is being applied (the result should have reasonable probabilities)
        assertThat(result.getConfidence()).isBetween(0.0, 1.0);
        assertThat(result.getAllProbabilities())
                .allMatch(cp -> cp.getProbability() >= 0.0 && cp.getProbability() <= 1.0);
    }

    @Test
    void shouldHandleBatchClassification() throws Exception {
        List<String> texts = List.of(
                "Flat 9,Dodd canyon,South Debra,B42 3YU",
                "I'm in danger",
                "Everything is good now"
        );

        List<ClassificationResult> results = addressClassifier.classifyBatch(texts);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getPredictedLabel()).isEqualTo("has_address");
        assertThat(results.get(1).getPredictedLabel()).isEqualTo("no_address");
        assertThat(results.get(2).getPredictedLabel()).isEqualTo("no_address");
    }

    @Test
    void shouldHandleComplexScenarios() {
        // Address + VODA + No Risk
        String text1 = "Flat 123,Bernard's house,B1C 0RF,Currently in women's refuge";

        ClassificationResult addressResult = addressClassifier.classify(text1);
        ClassificationResult vodaResult = vodaClassifier.classify(text1);
        ClassificationResult riskResult = riskClassifier.classify(text1);

        assertThat(addressResult.getPredictedLabel()).isEqualTo("has_address");
        assertThat(vodaResult.getPredictedLabel()).isEqualTo("has_voda");
        assertThat(riskResult.getPredictedLabel()).isEqualTo("no_risk");

        // No Address + No VODA + Risk
        String text2 = "I am in a tent and it's not safe, please help.";

        addressResult = addressClassifier.classify(text2);
        vodaResult = vodaClassifier.classify(text2);
        riskResult = riskClassifier.classify(text2);

        assertThat(addressResult.getPredictedLabel()).isEqualTo("no_address");
        assertThat(vodaResult.getPredictedLabel()).isEqualTo("no_voda");
        assertThat(riskResult.getPredictedLabel()).isEqualTo("has_risk");
    }

    private TextClassifier loadClassifier(String modelName) throws Exception {
        Path modelPath = projectRoot.resolve(SCRIPTS_DIR).resolve(modelName);

        return TextClassifierBuilder.newBuilder()
                .modelLoader(new FileSystemModelLoader())
                .modelPath(modelPath)
                .environment(ortEnvironment)
                .build();
    }

    private Path findProjectRoot() {
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
