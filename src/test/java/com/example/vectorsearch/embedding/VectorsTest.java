package com.example.vectorsearch.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.data.Offset.offset;

class VectorsTest {

    @Test
    void normalisesToUnitLength() {
        float[] normalised = Vectors.l2Normalize(new float[]{3, 4});

        assertThat(normalised).usingComparatorWithPrecision(1e-6f).containsExactly(0.6f, 0.8f);
    }

    @Test
    void leavesTheZeroVectorAlone() {
        assertThat(Vectors.l2Normalize(new float[]{0, 0, 0})).containsExactly(0f, 0f, 0f);
    }

    @Test
    void scoresIdenticalVectorsAsOne() {
        assertThat(Vectors.cosineSimilarity(new float[]{1, 2, 3}, new float[]{1, 2, 3}))
                .isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void scoresOppositeVectorsAsMinusOne() {
        assertThat(Vectors.cosineSimilarity(new float[]{1, 2}, new float[]{-1, -2}))
                .isCloseTo(-1.0, offset(1e-9));
    }

    @Test
    void scoresOrthogonalVectorsAsZero() {
        assertThat(Vectors.cosineSimilarity(new float[]{1, 0}, new float[]{0, 1})).isZero();
    }

    @Test
    void scoresAgainstTheZeroVectorAsZero() {
        assertThat(Vectors.cosineSimilarity(new float[]{0, 0}, new float[]{1, 1})).isZero();
    }

    @Test
    void rejectsMismatchedDimensions() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Vectors.cosineSimilarity(new float[]{1}, new float[]{1, 2}))
                .withMessageContaining("dimension mismatch");
    }

    @Test
    void roundTripsThroughItsStoredForm() {
        float[] original = {0.5f, -0.25f, 0f, 1234.5f};

        assertThat(Vectors.fromBytes(Vectors.toBytes(original))).isEqualTo(original);
    }

    @Test
    void storesFourBytesPerDimension() {
        assertThat(Vectors.toBytes(new float[256])).hasSize(1024);
    }

    @Test
    void rejectsBytesThatAreNotAWholeNumberOfFloats() {
        assertThatIllegalArgumentException().isThrownBy(() -> Vectors.fromBytes(new byte[7]));
    }

    @Test
    void detectsTheZeroVector() {
        assertThat(Vectors.isZero(new float[8])).isTrue();
        assertThat(Vectors.isZero(new float[]{0, 0, 0.1f})).isFalse();
    }
}
