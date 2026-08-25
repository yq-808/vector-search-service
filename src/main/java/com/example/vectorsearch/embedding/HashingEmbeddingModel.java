package com.example.vectorsearch.embedding;

import com.example.vectorsearch.config.VectorProperties;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A stand-in for a real embedding API, built on signed feature hashing.
 *
 * <p>It reproduces the two properties that matter for this service without calling a model:
 * <ul>
 *   <li><b>stable</b> &mdash; the same text always maps to the same vector, on any JVM, forever,
 *       because the feature set and the hash seed are fixed;</li>
 *   <li><b>proximity</b> &mdash; texts that share words and character n-grams land in the same
 *       dimensions with the same signs, so their cosine similarity is high. This is lexical
 *       proximity, not semantic understanding, which is all the exercise asks for.</li>
 * </ul>
 *
 * <p>Each feature is hashed once: the low bits choose the dimension and the top bit chooses the
 * sign. Signed hashing keeps collisions unbiased &mdash; unrelated features cancel out instead of
 * always adding up. The result is L2-normalised so cosine similarity is a plain dot product and
 * long documents do not outrank short ones.
 *
 * <p>Stateless, therefore safe to share across the worker threads.
 */
@Component
public class HashingEmbeddingModel implements EmbeddingModel {

    /** Fixed seed: changing it would invalidate every vector already in the database. */
    private static final HashFunction HASH = Hashing.murmur3_32_fixed(0x9747b28c);

    /** Anything that is not a letter or a digit separates tokens. */
    private static final Pattern SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final int MIN_GRAM = 2;
    private static final int MAX_GRAM = 3;

    /** A whole-word match is stronger evidence than an n-gram match, so it counts double. */
    private static final float WORD_WEIGHT = 2.0f;
    private static final float GRAM_WEIGHT = 1.0f;

    private final int dimension;

    public HashingEmbeddingModel(VectorProperties properties) {
        this.dimension = properties.embedding().dimension();
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        if (StringUtils.isBlank(text)) {
            return vector;
        }
        for (String token : tokenize(text)) {
            add(vector, "w:" + token, WORD_WEIGHT);
            for (int size = MIN_GRAM; size <= MAX_GRAM; size++) {
                for (String gram : charGrams(token, size)) {
                    add(vector, "g:" + gram, GRAM_WEIGHT);
                }
            }
        }
        return Vectors.l2Normalize(vector);
    }

    private void add(float[] vector, String feature, float weight) {
        int hash = HASH.hashUnencodedChars(feature).asInt();
        int index = Math.floorMod(hash, dimension);
        float sign = (hash >>> 31) == 0 ? 1f : -1f;
        vector[index] += sign * weight;
    }

    private static List<String> tokenize(String text) {
        return Arrays.stream(SEPARATOR.split(text.toLowerCase(Locale.ROOT)))
                .filter(StringUtils::isNotEmpty)
                .toList();
    }

    /**
     * Sliding character n-grams. They are what makes "vector search" close to "vector searching",
     * and they carry the whole signal for scripts written without spaces, such as Chinese.
     */
    private static List<String> charGrams(String token, int size) {
        if (token.length() < size) {
            return List.of();
        }
        List<String> grams = new ArrayList<>(token.length() - size + 1);
        for (int i = 0; i + size <= token.length(); i++) {
            grams.add(token.substring(i, i + size));
        }
        return grams;
    }
}
