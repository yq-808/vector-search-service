package com.example.vectorsearch.search;

import com.example.vectorsearch.config.VectorProperties;
import com.example.vectorsearch.document.Document;
import com.example.vectorsearch.document.DocumentRepository;
import com.example.vectorsearch.document.DocumentVector;
import com.example.vectorsearch.embedding.EmbeddingModel;
import com.example.vectorsearch.embedding.Vectors;
import com.google.common.collect.Comparators;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Synchronous top-K retrieval over the documents that have finished vectorising.
 *
 * <p>Not annotated {@code @Transactional} on purpose: the scan, the hit-count update and the final
 * load are three short database interactions with a pure CPU phase in between, and none of them
 * needs to see the others atomically.
 *
 * <p>The scan is a brute-force pass over every eligible vector, which is the honest choice at this
 * scale (exact results, no index to keep in sync). A corpus large enough to feel it would call for
 * an approximate index instead.
 */
@Service
public class SearchService {

    private final DocumentRepository documentRepository;
    private final EmbeddingModel embeddingModel;
    private final VectorProperties.Search settings;

    public SearchService(DocumentRepository documentRepository,
                         EmbeddingModel embeddingModel,
                         VectorProperties properties) {
        this.documentRepository = documentRepository;
        this.embeddingModel = embeddingModel;
        this.settings = properties.search();
    }

    /**
     * Embeds the query with the same model used at ingestion and returns the most similar
     * documents, highest score first. Invalidated documents and documents whose vector is not ready
     * are never candidates. Every returned document has its hit counter incremented.
     *
     * @param channel restricts the search to one channel when non-null
     */
    public List<SearchHit> search(String query, @Nullable Integer requestedTopK, @Nullable String channel) {
        int topK = resolveTopK(requestedTopK);
        float[] queryVector = embeddingModel.embed(query);
        if (Vectors.isZero(queryVector)) {
            return List.of();
        }

        List<ScoredDocument> best = documentRepository.findSearchableVectors(channel).stream()
                .map(candidate -> score(queryVector, candidate))
                .filter(scored -> scored.score() > settings.minScore())
                .collect(Comparators.greatest(topK, Comparator.comparingDouble(ScoredDocument::score)));
        if (best.isEmpty()) {
            return List.of();
        }

        List<String> ids = best.stream().map(ScoredDocument::documentId).toList();
        documentRepository.incrementHitCounts(ids);

        Map<String, Document> documents = documentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
        return best.stream()
                .map(scored -> new SearchHit(documents.get(scored.documentId()), scored.score()))
                .filter(hit -> hit.document() != null)
                .toList();
    }

    public int maxTopK() {
        return settings.maxTopK();
    }

    private ScoredDocument score(float[] queryVector, DocumentVector candidate) {
        double similarity = Vectors.cosineSimilarity(queryVector, Vectors.fromBytes(candidate.embedding()));
        return new ScoredDocument(candidate.documentId(), similarity);
    }

    private int resolveTopK(@Nullable Integer requested) {
        int topK = requested == null ? settings.defaultTopK() : requested;
        if (topK < 1 || topK > settings.maxTopK()) {
            throw new IllegalArgumentException(
                    "topK must be between 1 and " + settings.maxTopK() + " but was " + topK);
        }
        return topK;
    }
}
