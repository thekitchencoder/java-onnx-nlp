package uk.codery.onnx.nlp.preprocessing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BasicTextPreprocessorTest {

    @Test
    void shouldLowercaseText() {
        BasicTextPreprocessor preprocessor = BasicTextPreprocessor.builder()
                .lowercase(true)
                .build();

        String result = preprocessor.preprocess("Hello World");

        assertThat(result).isEqualTo("hello world");
    }

    @Test
    void shouldNormalizeWhitespace() {
        BasicTextPreprocessor preprocessor = BasicTextPreprocessor.builder()
                .normalizeWhitespace(true)
                .build();

        String result = preprocessor.preprocess("Hello    World  \t  Test");

        assertThat(result).isEqualTo("Hello World Test");
    }

    @Test
    void shouldRemoveUrls() {
        BasicTextPreprocessor preprocessor = BasicTextPreprocessor.builder()
                .removeUrls(true)
                .normalizeWhitespace(true)
                .build();

        String result = preprocessor.preprocess("Check out https://example.com for more info");

        assertThat(result).isEqualTo("Check out for more info");
    }

    @Test
    void shouldRemoveEmails() {
        BasicTextPreprocessor preprocessor = BasicTextPreprocessor.builder()
                .removeEmails(true)
                .normalizeWhitespace(true)
                .build();

        String result = preprocessor.preprocess("Contact me at test@example.com");

        assertThat(result).isEqualTo("Contact me at");
    }

    @Test
    void shouldRemoveMentions() {
        BasicTextPreprocessor preprocessor = BasicTextPreprocessor.builder()
                .removeMentions(true)
                .normalizeWhitespace(true)
                .build();

        String result = preprocessor.preprocess("Hey @user check this out");

        assertThat(result).isEqualTo("Hey check this out");
    }

    @Test
    void shouldApplyAllTransformations() {
        BasicTextPreprocessor preprocessor = BasicTextPreprocessor.builder()
                .lowercase(true)
                .removeUrls(true)
                .normalizeWhitespace(true)
                .trim(true)
                .build();

        String result = preprocessor.preprocess("  Hello WORLD  https://example.com  ");

        assertThat(result).isEqualTo("hello world");
    }

    @Test
    void shouldUseDefaultSettings() {
        BasicTextPreprocessor preprocessor = BasicTextPreprocessor.createDefault();

        String result = preprocessor.preprocess("  Hello   WORLD  ");

        assertThat(result).isEqualTo("hello world");
    }
}
