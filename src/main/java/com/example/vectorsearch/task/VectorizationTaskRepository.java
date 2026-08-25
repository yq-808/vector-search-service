package com.example.vectorsearch.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Task state transitions are expressed as conditional updates: each one states the status it
 * expects to find, and reports how many rows it actually changed. A caller that changed zero rows
 * knows another thread got there first and backs off, which is what keeps the workers safe without
 * any locking of our own.
 */
public interface VectorizationTaskRepository extends JpaRepository<VectorizationTask, String> {

    Optional<VectorizationTask> findFirstByDocumentIdOrderByCreatedAtDescIdDesc(String documentId);

    @Query("select t.id from VectorizationTask t where t.status = :status order by t.createdAt")
    List<String> findIdsByStatus(@Param("status") TaskStatus status);

    @Query("select new com.example.vectorsearch.task.TaskStatusCount(t.status, count(t)) "
            + "from VectorizationTask t group by t.status order by t.status")
    List<TaskStatusCount> countByStatus();

    @Transactional
    @Modifying
    @Query("""
            update VectorizationTask t
               set t.status = :to, t.startedAt = :at
             where t.id = :taskId and t.status = :from
            """)
    int updateStart(@Param("taskId") String taskId,
                    @Param("from") TaskStatus from,
                    @Param("to") TaskStatus to,
                    @Param("at") Instant at);

    @Transactional
    @Modifying
    @Query("""
            update VectorizationTask t
               set t.status = :to, t.errorMessage = :errorMessage, t.finishedAt = :at
             where t.id = :taskId and t.status = :from
            """)
    int updateFinish(@Param("taskId") String taskId,
                     @Param("from") TaskStatus from,
                     @Param("to") TaskStatus to,
                     @Param("errorMessage") String errorMessage,
                     @Param("at") Instant at);

    @Transactional
    @Modifying
    @Query("""
            update VectorizationTask t
               set t.status = :to, t.finishedAt = :at
             where t.documentId = :documentId and t.status in :from
            """)
    int updateAllForDocument(@Param("documentId") String documentId,
                             @Param("from") Collection<TaskStatus> from,
                             @Param("to") TaskStatus to,
                             @Param("at") Instant at);

    @Transactional
    @Modifying
    @Query("""
            update VectorizationTask t
               set t.status = :to, t.startedAt = null
             where t.id = :taskId and t.status = :from
            """)
    int updateRequeue(@Param("taskId") String taskId,
                      @Param("from") TaskStatus from,
                      @Param("to") TaskStatus to);

    @Transactional
    @Modifying
    @Query("update VectorizationTask t set t.status = :to, t.startedAt = null where t.status = :from")
    int updateAllByStatus(@Param("from") TaskStatus from, @Param("to") TaskStatus to);

    /** Claims a queued task for this worker. False means somebody else already has it. */
    default boolean claim(String taskId, Instant at) {
        return updateStart(taskId, TaskStatus.QUEUED, TaskStatus.RUNNING, at) == 1;
    }

    /** Ends a running task. False means it was superseded while running, so its result is stale. */
    default boolean finish(String taskId, TaskStatus outcome, String errorMessage, Instant at) {
        return updateFinish(taskId, TaskStatus.RUNNING, outcome, errorMessage, at) == 1;
    }

    /** Puts an interrupted task back in line, so shutdown never loses work. */
    default boolean requeue(String taskId) {
        return updateRequeue(taskId, TaskStatus.RUNNING, TaskStatus.QUEUED) == 1;
    }

    /** Cancels whatever is still in flight for a document, because newer content just arrived. */
    default int cancelInFlight(String documentId, Instant at) {
        return updateAllForDocument(documentId, TaskStatus.IN_FLIGHT, TaskStatus.CANCELLED, at);
    }

    /** After an unclean shutdown, tasks left RUNNING have no worker; make them queueable again. */
    default int resetOrphanedTasks() {
        return updateAllByStatus(TaskStatus.RUNNING, TaskStatus.QUEUED);
    }
}
