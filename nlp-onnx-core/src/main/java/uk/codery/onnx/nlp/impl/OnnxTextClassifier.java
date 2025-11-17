package uk.codery.onnx.nlp.impl;

import ai.onnxruntime.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import uk.codery.onnx.nlp.api.ClassificationResult;
import uk.codery.onnx.nlp.api.TextClassifier;
import uk.codery.onnx.nlp.api.TextPreprocessor;
import uk.codery.onnx.nlp.calibration.Calibrator;
import uk.codery.onnx.nlp.calibration.CalibratorFactory;
import uk.codery.onnx.nlp.model.ModelBundle;
import uk.codery.onnx.nlp.model.ModelConfig;
import uk.codery.onnx.nlp.preprocessing.NoOpPreprocessor;
import uk.codery.onnx.nlp.tokenization.Tokenizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ONNX-based text classifier implementation.
 * Thread-safe for concurrent inference.
 */
@Slf4j
public class OnnxTextClassifier implements TextClassifier {

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final ModelConfig config;
    private final Tokenizer tokenizer;
    private final TextPreprocessor preprocessor;
    private final Calibrator calibrator;
    private final boolean ownsEnvironment;
    private final boolean usesStringInput;

    /**
     * Creates a classifier from a model bundle.
     * Creates its own OrtEnvironment.
     *
     * @param modelBundle the model bundle
     * @param tokenizer the tokenizer to use
     * @param preprocessor the text preprocessor
     * @throws OrtException if model loading fails
     */
    public OnnxTextClassifier(
            @NonNull ModelBundle modelBundle,
            @NonNull Tokenizer tokenizer,
            @NonNull TextPreprocessor preprocessor
    ) throws OrtException {
        this(OrtEnvironment.getEnvironment(), modelBundle, tokenizer, preprocessor, true);
    }

    /**
     * Creates a classifier with a shared OrtEnvironment.
     *
     * @param environment the ONNX runtime environment
     * @param modelBundle the model bundle
     * @param tokenizer the tokenizer to use (null for STRING-input models)
     * @param preprocessor the text preprocessor
     * @param ownsEnvironment whether this instance owns the environment and should close it
     * @throws OrtException if model loading fails
     */
    public OnnxTextClassifier(
            @NonNull OrtEnvironment environment,
            @NonNull ModelBundle modelBundle,
            Tokenizer tokenizer,
            @NonNull TextPreprocessor preprocessor,
            boolean ownsEnvironment
    ) throws OrtException {
        this.environment = environment;
        this.config = modelBundle.getConfig();
        this.tokenizer = tokenizer;
        this.preprocessor = preprocessor != null ? preprocessor : new NoOpPreprocessor();
        this.calibrator = CalibratorFactory.create(modelBundle.getCalibration());
        this.ownsEnvironment = ownsEnvironment;

        // Create session options
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

        // Load model
        this.session = environment.createSession(modelBundle.getModelBytes(), sessionOptions);

        // Detect input tensor type
        this.usesStringInput = detectStringInput();

        log.info("Loaded ONNX model: {} v{} (input type: {})",
                config.getModelName(),
                config.getVersion(),
                usesStringInput ? "STRING" : "LONG");
    }

    /**
     * Detects if the model expects string inputs by checking the input tensor type.
     */
    private boolean detectStringInput() {
        try {
            String inputName = config.getInputTensorName();
            Map<String, NodeInfo> inputInfo = session.getInputInfo();

            if (inputInfo.containsKey(inputName)) {
                NodeInfo nodeInfo = inputInfo.get(inputName);
                if (nodeInfo.getInfo() instanceof TensorInfo tensorInfo) {
                    OnnxJavaType javaType = tensorInfo.type;
                    return javaType == OnnxJavaType.STRING;
                }
            }

            log.warn("Could not detect input tensor type, assuming LONG");
            return false;
        } catch (Exception e) {
            log.warn("Error detecting input tensor type, assuming LONG", e);
            return false;
        }
    }

    @Override
    public ClassificationResult classify(@NonNull String text) {
        return classifyBatch(List.of(text)).get(0);
    }

    @Override
    public List<ClassificationResult> classifyBatch(@NonNull List<String> texts) {
        try {
            // Preprocess
            List<String> preprocessedTexts = texts.stream()
                    .map(preprocessor::preprocess)
                    .toList();

            OnnxTensor tensor;

            if (usesStringInput) {
                // For STRING input models (e.g., TF-IDF), pass preprocessed text directly
                String[][] textArray = new String[preprocessedTexts.size()][1];
                for (int i = 0; i < preprocessedTexts.size(); i++) {
                    textArray[i][0] = preprocessedTexts.get(i);
                }
                tensor = OnnxTensor.createTensor(environment, textArray);
            } else {
                // For token-based models, tokenize first
                if (tokenizer == null) {
                    throw new IllegalStateException(
                            "Tokenizer is required for token-based models but was not provided");
                }

                long[][] inputIds = tokenizer.tokenizeBatch(
                        preprocessedTexts,
                        config.getMaxSequenceLength()
                );

                tensor = OnnxTensor.createTensor(environment, inputIds);
            }

            // Run inference
            Map<String, OnnxTensor> inputs = Map.of(config.getInputTensorName(), tensor);
            OrtSession.Result result = session.run(inputs);

            // Extract probabilities
            float[][] rawOutput = (float[][]) result.get(config.getOutputTensorName())
                    .orElseThrow(() -> new RuntimeException("No output tensor found"))
                    .getValue();

            // Build results
            List<ClassificationResult> results = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) {
                results.add(buildResult(rawOutput[i]));
            }

            // Cleanup
            tensor.close();
            result.close();

            return results;

        } catch (OrtException e) {
            throw new RuntimeException("Inference failed", e);
        }
    }

    private ClassificationResult buildResult(float[] rawProbabilities) {
        // Convert to double and apply calibration
        double[] probs = new double[rawProbabilities.length];
        for (int i = 0; i < rawProbabilities.length; i++) {
            probs[i] = rawProbabilities[i];
        }

        double[] calibratedProbs = calibrator.calibrate(probs);

        // Find best class
        int bestIdx = 0;
        double bestProb = calibratedProbs[0];
        for (int i = 1; i < calibratedProbs.length; i++) {
            if (calibratedProbs[i] > bestProb) {
                bestProb = calibratedProbs[i];
                bestIdx = i;
            }
        }

        // Build all probabilities list
        List<ClassificationResult.ClassProbability> allProbs = new ArrayList<>();
        for (int i = 0; i < calibratedProbs.length; i++) {
            allProbs.add(ClassificationResult.ClassProbability.builder()
                    .label(config.getClassLabels().get(i))
                    .probability(calibratedProbs[i])
                    .build());
        }

        return ClassificationResult.builder()
                .predictedLabel(config.getClassLabels().get(bestIdx))
                .confidence(bestProb)
                .allProbabilities(allProbs)
                .calibrated(!calibrator.isIdentity())
                .build();
    }

    @Override
    public void close() {
        try {
            if (session != null) {
                session.close();
            }
            if (ownsEnvironment && environment != null) {
                environment.close();
            }
            log.debug("Closed ONNX classifier");
        } catch (OrtException e) {
            log.warn("Error closing ONNX classifier", e);
        }
    }
}
