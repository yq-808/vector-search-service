package com.example.vectorsearch.embedding;

import com.google.common.base.Preconditions;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Vector maths and the on-disk encoding used to persist embeddings.
 *
 * <p>Vectors are stored as big-endian IEEE-754 float32, so a 256-dimension embedding
 * occupies exactly 1 KiB and round-trips bit-for-bit through the database.
 */
public final class Vectors {

    public static final int BYTES_PER_FLOAT = Float.BYTES;

    private Vectors() {
    }

    /** Scales {@code vector} in place to unit length; a zero vector is left untouched. */
    public static float[] l2Normalize(float[] vector) {
        double sumOfSquares = 0;
        for (float value : vector) {
            sumOfSquares += (double) value * value;
        }
        double norm = Math.sqrt(sumOfSquares);
        if (norm == 0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }

    /** Cosine similarity in [-1, 1]; returns 0 when either side is a zero vector. */
    public static double cosineSimilarity(float[] left, float[] right) {
        Preconditions.checkArgument(left.length == right.length,
                "dimension mismatch: %s vs %s", left.length, right.length);
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += (double) left[i] * right[i];
            leftNorm += (double) left[i] * left[i];
            rightNorm += (double) right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    public static boolean isZero(float[] vector) {
        for (float value : vector) {
            if (value != 0f) {
                return false;
            }
        }
        return true;
    }

    public static byte[] toBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * BYTES_PER_FLOAT);
        buffer.asFloatBuffer().put(vector);
        return buffer.array();
    }

    public static float[] fromBytes(byte[] bytes) {
        Preconditions.checkArgument(bytes.length % BYTES_PER_FLOAT == 0,
                "not a float32 vector: %s bytes", bytes.length);
        FloatBuffer buffer = ByteBuffer.wrap(bytes).asFloatBuffer();
        float[] vector = new float[buffer.remaining()];
        buffer.get(vector);
        return vector;
    }
}
