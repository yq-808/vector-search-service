package com.example.vectorsearch.embedding;

import com.example.vectorsearch.config.VectorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class HashingEmbeddingModelTest {

    private static final int DIMENSION = 256;

    private final EmbeddingModel model = new HashingEmbeddingModel(properties(DIMENSION));

    @Test
    void alwaysProducesTheConfiguredDimension() {
        assertThat(model.embed("anything at all")).hasSize(DIMENSION);
        assertThat(model.embed("")).hasSize(DIMENSION);
    }

    @Test
    void isDeterministicForTheSameText() {
        assertThat(model.embed("stable input")).isEqualTo(model.embed("stable input"));
    }

    @Test
    void isDeterministicAcrossInstances() {
        EmbeddingModel other = new HashingEmbeddingModel(properties(DIMENSION));

        assertThat(model.embed("stable input")).isEqualTo(other.embed("stable input"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void mapsBlankTextToTheZeroVector(String blank) {
        assertThat(Vectors.isZero(model.embed(blank))).isTrue();
    }

    @Test
    void mapsNullToTheZeroVector() {
        assertThat(Vectors.isZero(model.embed(null))).isTrue();
    }

    @Test
    void producesUnitVectorsForRealText() {
        float[] vector = model.embed("a document worth embedding");

        assertThat(Vectors.cosineSimilarity(vector, vector)).isCloseTo(1.0, offset(1e-6));
    }

    @Test
    void scoresRelatedTextAboveUnrelatedText() {
        float[] query = model.embed("vector search over embeddings");
        float[] related = model.embed("searching vectors and embeddings at scale");
        float[] unrelated = model.embed("banana bread recipe with walnuts");

        assertThat(Vectors.cosineSimilarity(query, related))
                .isGreaterThan(Vectors.cosineSimilarity(query, unrelated));
    }

    @Test
    void scoresRelatedChineseTextAboveUnrelatedChineseText() {
        float[] query = model.embed("北京今天的天气怎么样");
        float[] related = model.embed("北京明天的天气如何");
        float[] unrelated = model.embed("上海飞往巴黎的机票价格");

        assertThat(Vectors.cosineSimilarity(query, related))
                .isGreaterThan(Vectors.cosineSimilarity(query, unrelated));
    }

    @Test
    void treatsCaseAndPunctuationAsNoise() {
        assertThat(model.embed("Vector Search!")).isEqualTo(model.embed("vector, search"));
    }

    @Test
    void honoursANonDefaultDimension() {
        assertThat(new HashingEmbeddingModel(properties(64)).embed("text")).hasSize(64);
    }

    private static VectorProperties properties(int dimension) {
        return new VectorProperties(
                new VectorProperties.Embedding(dimension),
                new VectorProperties.Vectorization(1, 10, 0, 1000),
                new VectorProperties.Search(10, 100, 0.0));
    }
}
