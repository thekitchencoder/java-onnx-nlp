package examples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import uk.codery.onnx.nlp.api.ClassificationResult;
import uk.codery.onnx.nlp.api.TextClassifier;

import java.util.List;

/**
 * Example Spring Boot application using the NLP ONNX starter.
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

    private final TextClassifier classifier;

    public ClassificationController(TextClassifier classifier) {
        this.classifier = classifier;
    }

    @PostMapping("/single")
    public ClassificationResponse classifySingle(@RequestBody TextRequest request) {
        ClassificationResult result = classifier.classify(request.text());
        return new ClassificationResponse(
                result.getPredictedLabel(),
                result.getConfidence(),
                result.isCalibrated()
        );
    }

    @PostMapping("/batch")
    public List<ClassificationResponse> classifyBatch(@RequestBody BatchRequest request) {
        List<ClassificationResult> results = classifier.classifyBatch(request.texts());
        return results.stream()
                .map(r -> new ClassificationResponse(
                        r.getPredictedLabel(),
                        r.getConfidence(),
                        r.isCalibrated()
                ))
                .toList();
    }
}

record TextRequest(String text) {}
record BatchRequest(List<String> texts) {}
record ClassificationResponse(String label, double confidence, boolean calibrated) {}
