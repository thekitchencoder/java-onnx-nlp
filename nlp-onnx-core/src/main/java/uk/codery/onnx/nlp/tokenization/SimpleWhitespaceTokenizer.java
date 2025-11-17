package uk.codery.onnx.nlp.tokenization;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;

/**
 * Simple whitespace-based tokenizer using a vocabulary map.
 * This is a basic implementation - for production, use a proper tokenizer
 * like HuggingFace tokenizers or SentencePiece.
 */
@RequiredArgsConstructor
public class SimpleWhitespaceTokenizer implements Tokenizer {

    private final Map<String, Integer> vocabulary;
    private final int unknownTokenId;
    private final int paddingTokenId;

    @Override
    public long[] tokenize(@NonNull String text, int maxLength) {
        String[] words = text.split("\\s+");
        long[] tokens = new long[maxLength];

        // Fill with padding token
        Arrays.fill(tokens, paddingTokenId);

        // Tokenize up to maxLength
        int limit = Math.min(words.length, maxLength);
        for (int i = 0; i < limit; i++) {
            tokens[i] = vocabulary.getOrDefault(words[i], unknownTokenId);
        }

        return tokens;
    }

    /**
     * Creates a tokenizer from a vocabulary map.
     * Assumes vocabulary contains special tokens: [PAD], [UNK]
     */
    public static SimpleWhitespaceTokenizer fromVocabulary(@NonNull Map<String, Integer> vocabulary) {
        int unknownId = vocabulary.getOrDefault("[UNK]", 0);
        int padId = vocabulary.getOrDefault("[PAD]", 0);
        return new SimpleWhitespaceTokenizer(vocabulary, unknownId, padId);
    }
}
