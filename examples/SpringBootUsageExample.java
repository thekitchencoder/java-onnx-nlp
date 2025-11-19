package examples;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import uk.codery.onnx.nlp.api.ClassificationResult;
import uk.codery.onnx.nlp.api.TextClassifier;

import java.util.List;

/**
 * Example Spring Boot application using the NLP ONNX starter with multiple models.
 */
@SpringBootApplication
public class SpringBootUsageExample {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootUsageExample.class, args);
    }
}

@RestController
@RequestMapping("/api/classify")
class ClassificationController {

    private final TextClassifier addressClassifier;
    private final TextClassifier sentimentClassifier;
    private final TextClassifier riskClassifier;

    public ClassificationController(
            @Qualifier("addressClassifier") TextClassifier addressClassifier,
            @Qualifier("sentimentClassifier") TextClassifier sentimentClassifier,
            @Qualifier("riskClassifier") TextClassifier riskClassifier
    ) {
        this.addressClassifier = addressClassifier;
        this.sentimentClassifier = sentimentClassifier;
        this.riskClassifier = riskClassifier;
    }

    @PostMapping("/address")
    public ClassificationResponse classifyAddress(@RequestBody TextRequest request) {
        ClassificationResult result = addressClassifier.classify(request.text());
        return toResponse(result);
    }

    @PostMapping("/sentiment")
    public ClassificationResponse classifySentiment(@RequestBody TextRequest request) {
        ClassificationResult result = sentimentClassifier.classify(request.text());
        return toResponse(result);
    }

    @PostMapping("/risk")
    public ClassificationResponse classifyRisk(@RequestBody TextRequest request) {
        ClassificationResult result = riskClassifier.classify(request.text());
        return toResponse(result);
    }

    @PostMapping("/all")
    public MultiClassificationResponse classifyAll(@RequestBody TextRequest request) {
        return new MultiClassificationResponse(
                toResponse(addressClassifier.classify(request.text())),
                toResponse(sentimentClassifier.classify(request.text())),
                toResponse(riskClassifier.classify(request.text()))
        );
    }

    @PostMapping("/address/batch")
    public List<ClassificationResponse> classifyAddressBatch(@RequestBody BatchRequest request) {
        List<ClassificationResult> results = addressClassifier.classifyBatch(request.texts());
        return results.stream().map(this::toResponse).toList();
    }

    private ClassificationResponse toResponse(ClassificationResult result) {
        return new ClassificationResponse(
                result.getPredictedLabel(),
                result.getConfidence(),
                result.isCalibrated()
        );
    }
}

record TextRequest(String text) {}
record BatchRequest(List<String> texts) {}
record ClassificationResponse(String label, double confidence, boolean calibrated) {}
record MultiClassificationResponse(
        ClassificationResponse address,
        ClassificationResponse sentiment,
        ClassificationResponse risk
) {}
