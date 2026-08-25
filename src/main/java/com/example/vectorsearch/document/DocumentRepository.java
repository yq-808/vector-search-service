package com.example.vectorsearch.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, String> {

    @Query("""
            select new com.example.vectorsearch.document.DocumentVector(d.id, d.embedding)
            from Document d
            where d.vectorReady = true
              and d.status = :status
              and (:channel is null or d.channel = :channel)
            """)
    List<DocumentVector> findVectors(@Param("status") DocumentStatus status,
                                     @Param("channel") @Nullable String channel);

    /** Candidates for retrieval: vectorised, still valid, optionally restricted to one channel. */
    default List<DocumentVector> findSearchableVectors(@Nullable String channel) {
        return findVectors(DocumentStatus.ACTIVE, channel);
    }

    Page<Document> findByChannel(String channel, Pageable pageable);

    Page<Document> findByStatus(DocumentStatus status, Pageable pageable);

    Page<Document> findByChannelAndStatus(String channel, DocumentStatus status, Pageable pageable);

    /**
     * Bumps the hit counters of the documents a search returned.
     *
     * <p>Deliberately a single atomic SQL statement rather than a read-modify-write: concurrent
     * searches over the same document then serialise on the row lock instead of losing counts.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update document set hit_count = hit_count + 1 where id in (:ids)", nativeQuery = true)
    int incrementHitCounts(@Param("ids") Collection<String> ids);

    long countByStatus(DocumentStatus status);

    long countByVectorReadyTrue();

    @Query("select coalesce(sum(d.hitCount), 0) from Document d")
    long totalHits();

    @Query("""
            select new com.example.vectorsearch.document.ChannelStats(
                       d.channel, count(d), coalesce(sum(d.hitCount), 0))
            from Document d
            group by d.channel
            order by d.channel
            """)
    List<ChannelStats> statsByChannel();
}
