package uk.codery.onnx.nlp.preprocessing;

import lombok.Builder;
import lombok.NonNull;
import uk.codery.onnx.nlp.api.TextPreprocessor;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Basic text preprocessor with common NLP preprocessing steps.
 */
@Builder
public class BasicTextPreprocessor implements TextPreprocessor {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[\\w.-]+(?:\\.[\\w.-]+)+[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]*"
    );
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\w+");
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#\\w+");

    @Builder.Default
    private final boolean lowercase = false;

    @Builder.Default
    private final boolean removeUrls = false;

    @Builder.Default
    private final boolean removeEmails = false;

    @Builder.Default
    private final boolean removeMentions = false;

    @Builder.Default
    private final boolean removeHashtags = false;

    @Builder.Default
    private final boolean normalizeWhitespace = true;

    @Builder.Default
    private final boolean unicodeNormalization = true;

    @Builder.Default
    private final boolean trim = true;

    @Override
    public String preprocess(@NonNull String text) {
        String processed = text;

        // Unicode normalization (NFD form)
        if (unicodeNormalization) {
            processed = Normalizer.normalize(processed, Normalizer.Form.NFD);
        }

        // Remove URLs
        if (removeUrls) {
            processed = URL_PATTERN.matcher(processed).replaceAll(" ");
        }

        // Remove emails
        if (removeEmails) {
            processed = EMAIL_PATTERN.matcher(processed).replaceAll(" ");
        }

        // Remove mentions
        if (removeMentions) {
            processed = MENTION_PATTERN.matcher(processed).replaceAll(" ");
        }

        // Remove hashtags (keep the text after #)
        if (removeHashtags) {
            processed = HASHTAG_PATTERN.matcher(processed).replaceAll(" ");
        }

        // Lowercase
        if (lowercase) {
            processed = processed.toLowerCase();
        }

        // Normalize whitespace
        if (normalizeWhitespace) {
            processed = WHITESPACE_PATTERN.matcher(processed).replaceAll(" ");
        }

        // Trim
        if (trim) {
            processed = processed.trim();
        }

        return processed;
    }

    /**
     * Creates a default preprocessor with common settings.
     */
    public static BasicTextPreprocessor createDefault() {
        return BasicTextPreprocessor.builder()
                .lowercase(true)
                .normalizeWhitespace(true)
                .trim(true)
                .build();
    }
}
