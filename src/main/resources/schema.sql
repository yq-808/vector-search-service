-- Schema for the vector search service.
-- Applied automatically at startup (spring.sql.init.mode=always) and written to be idempotent,
-- so the H2 file database can be reused across restarts. Hibernate never generates DDL.

CREATE TABLE IF NOT EXISTS document (
    id            VARCHAR(64)              NOT NULL,
    content       CLOB                     NOT NULL,
    channel       VARCHAR(64)              NOT NULL DEFAULT 'default',
    status        VARCHAR(16)              NOT NULL DEFAULT 'ACTIVE',
    vector_ready  BOOLEAN                  NOT NULL DEFAULT FALSE,
    embedding     VARBINARY(8192),
    hit_count     BIGINT                   NOT NULL DEFAULT 0,
    submitted_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    vectorized_at TIMESTAMP WITH TIME ZONE,
    version       BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_document PRIMARY KEY (id)
);

-- Retrieval always filters on channel and status, and only ever scans ready vectors.
CREATE INDEX IF NOT EXISTS idx_document_lookup ON document (status, vector_ready, channel);

CREATE TABLE IF NOT EXISTS vectorization_task (
    id            VARCHAR(64)              NOT NULL,
    document_id   VARCHAR(64)              NOT NULL,
    status        VARCHAR(16)              NOT NULL,
    error_message VARCHAR(1000),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at    TIMESTAMP WITH TIME ZONE,
    finished_at   TIMESTAMP WITH TIME ZONE,
    version       BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_vectorization_task PRIMARY KEY (id),
    CONSTRAINT fk_task_document FOREIGN KEY (document_id) REFERENCES document (id)
);

-- Startup recovery scans by status; the document view reads the newest task of one document.
CREATE INDEX IF NOT EXISTS idx_task_status ON vectorization_task (status, created_at);
CREATE INDEX IF NOT EXISTS idx_task_document ON vectorization_task (document_id, created_at);
