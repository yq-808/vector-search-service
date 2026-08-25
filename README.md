# vector-search-service

A small retrieval service: documents are submitted, vectorised **asynchronously** by an in-process
queue, and then retrievable by cosine similarity — with full provenance on every document.

There is no embedding API involved. Vectors come from a deterministic hashing model that reproduces
the two properties retrieval actually depends on: the same text always yields the same vector, and
similar text yields nearby vectors.

```
POST /documents ──► document row + task row ──► queue ──► worker ──► vector stored
     (202, task id)        (committed first)              (256 floats)      │
                                                                           ▼
                              POST /search ──► cosine over ready vectors ──► top-K + hit counted
```

## Requirements

Java 17 or newer (built and tested on 21) and Maven. Nothing else — H2 is embedded and the queue
is a plain `LinkedBlockingQueue`.

## Quick start

```bash
mvn spring-boot:run
```

The service listens on `http://localhost:8080`. The database file is created at
`./data/vector-search.mv.db` and survives restarts; `schema.sql` is applied at every startup and is
idempotent. To see the whole API in action:

```bash
./scripts/demo.sh
```

To browse the database while it runs, start with `--spring.profiles.active=dev`, which exposes the
H2 console at `/h2-console`. It is off by default: it is a database shell on an open port.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/documents` | Submit a document. Returns **202** and a task id — the vector does not exist yet. |
| `GET` | `/api/v1/documents` | List documents; filter by `channel`, `status`, page with `page`/`size`. |
| `GET` | `/api/v1/documents/{id}` | Document detail: content, channel, timestamps, vector readiness, hit count. |
| `POST` | `/api/v1/documents/{id}/invalidate` | Retire a document. It keeps its history but stops matching. |
| `GET` | `/api/v1/documents/{id}/task` | The newest vectorisation task of that document. |
| `GET` | `/api/v1/tasks/{taskId}` | Task status (`QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`) plus the document. |
| `POST` | `/api/v1/search` | Synchronous top-K retrieval. |
| `GET` | `/api/v1/stats` | Corpus and retrieval counters, per status and per channel. |

```bash
# ingest
curl -X POST localhost:8080/api/v1/documents -H 'Content-Type: application/json' \
  -d '{"documentId":"doc-1","content":"vector search over embeddings","channel":"docs"}'
# {"taskId":"7c2e...","documentId":"doc-1","status":"QUEUED"}

# poll
curl localhost:8080/api/v1/tasks/7c2e...

# retrieve
curl -X POST localhost:8080/api/v1/search -H 'Content-Type: application/json' \
  -d '{"query":"searching vectors","topK":5,"channel":"docs"}'
```

Every error comes back in the same `{timestamp, status, error, message}` shape: `404` unknown
document, task or path, `400` invalid request, `409` concurrent modification of the same document,
`503` vectorisation backlog full, `500` anything unforeseen. That holds for the failures Spring MVC
raises before a controller is reached too — malformed body, wrong method, unconvertible query
parameter — because [`ApiExceptionHandler`](src/main/java/com/example/vectorsearch/api/ApiExceptionHandler.java)
extends `ResponseEntityExceptionHandler` rather than only catching domain exceptions. Unexpected
failures are logged in full and reported without internals.

Document ids are restricted to `[A-Za-z0-9._~-]`, since every document is addressed as
`/documents/{documentId}`.

## How it works

**The embedding** ([`HashingEmbeddingModel`](src/main/java/com/example/vectorsearch/embedding/HashingEmbeddingModel.java))
splits text into words and character 2- and 3-grams, hashes each feature with a fixed murmur3 seed,
and adds it to one of 256 dimensions — the low bits pick the dimension, the top bit picks the sign.
Signed hashing keeps collisions unbiased. The result is L2-normalised, so cosine similarity is a
plain dot product and long documents do not outrank short ones. Blank text maps to the zero vector,
which by construction matches nothing. This is lexical proximity, not semantic understanding, which
is exactly what a stand-in for an embedding API needs to be.

**The queue** ([`VectorizationQueue`](src/main/java/com/example/vectorsearch/vectorization/VectorizationQueue.java))
is a bounded `LinkedBlockingQueue` of task ids drained by a fixed set of worker threads. Only ids
travel through it; the task row in H2 is the durable copy. Three details make that safe:

- a task is published to the queue **after** its transaction commits, so a worker can never claim a
  row it cannot see yet;
- on shutdown, workers are interrupted and the in-flight task is returned to `QUEUED`, so no
  submission is lost;
- at startup, [`PendingTaskRecovery`](src/main/java/com/example/vectorsearch/vectorization/PendingTaskRecovery.java)
  re-queues everything still pending, including tasks orphaned by an unclean stop. A backlog left by
  a crash can be larger than the queue is allowed to hold, so it publishes on its own thread and
  waits for room instead of overflowing — refusing to start, or dropping the excess, would both be
  worse answers;
- the worker pool sits below the web server in the Spring lifecycle, so it starts before the first
  request arrives and stops only after HTTP has drained.

**Concurrency safety** is handled where the contention actually is, rather than with a lock around
the service:

- task state moves through *conditional* updates (`... where id = ? and status = ?`). A worker that
  changes zero rows knows another thread got there first and backs off, so two workers can never
  process the same task;
- re-submitting a document cancels whatever is still in flight for it. If a worker was mid-run, its
  `finish` matches no row and its now-stale vector is discarded;
- hit counts are incremented with one atomic `update ... set hit_count = hit_count + 1`, and the
  column is mapped non-updatable so no ordinary entity flush can write a stale value back over it;
- documents carry an optimistic-locking `@Version`; a losing writer gets `409` rather than silent
  data loss.

**Retrieval** ([`SearchService`](src/main/java/com/example/vectorsearch/search/SearchService.java))
embeds the query with the same model, scans the vectors of documents that are ready and valid
(optionally in one channel), and keeps the top K with a bounded heap. It runs as three short
database interactions with a pure-CPU phase in between, deliberately not inside one long
transaction. The scan is brute force — exact, with no index to keep in sync, which is the honest
choice at this scale. A corpus large enough to feel it would want an approximate index instead.

## Configuration

Everything tunable lives under `vector.*` in
[`application.yml`](src/main/resources/application.yml):

| Property | Default | Meaning |
| --- | --- | --- |
| `vector.embedding.dimension` | `256` | Vector length. |
| `vector.vectorization.workers` | `4` | Threads draining the queue. |
| `vector.vectorization.queue-capacity` | `10000` | Backlog before submissions get `503`. |
| `vector.vectorization.simulated-cost-millis` | `200` | Artificial latency standing in for a remote embedding call. |
| `vector.search.default-top-k` / `max-top-k` | `10` / `100` | Result-count default and ceiling. |
| `vector.search.min-score` | `0.0` | Similarities at or below this are treated as hash noise. |

## Layout

```
embedding/      the mock embedding model and vector maths
document/       document entity, repository, ingestion and lifecycle
task/           task entity and its conditional state transitions
vectorization/  the queue, the worker pool, startup recovery
search/         top-K retrieval
stats/          traceability rollups
api/            controllers, DTOs, error handling
```

## Tests

```bash
mvn test
```

69 tests, two kinds:

- **Unit** — the embedding model (determinism, dimension, zero vector, relative similarity in
  English and Chinese), vector maths, the queue, startup recovery, and the worker state machine
  with the database mocked out, including the interrupt path that shutdown takes.
- **Black box** — every integration test drives the running service over HTTP only, never reaching
  into a bean. They cover the document lifecycle, ranking, channel filtering, invalidation, hit
  counting, validation errors, the single error shape (including the failures Spring raises before
  a controller is reached), backpressure returning `503` once the queue is full, the asynchronous
  contract (a document is *not* searchable until its task finishes, and a re-submission cancels the
  in-flight one), 24 simultaneous submissions and 32 simultaneous searches counting exactly 32 hits,
  and a genuine stop-and-restart proving the H2 file database keeps documents and vectors.

## 需求对照

| 需求 | 实现 |
| --- | --- |
| 模拟向量化，不调用 LLM / Embedding API | `HashingEmbeddingModel`：固定种子的特征哈希 |
| 固定 256 维 float 数组 | `vector.embedding.dimension=256`，存为大端 float32（1 KiB） |
| 同一文本输出完全一致；空文本输出零向量 | `HashingEmbeddingModelTest` 覆盖 |
| 提交后返回任务 ID，后台队列异步向量化 | `POST /documents` 返回 202 + `taskId`；`VectorizationQueue` + `VectorizationWorkerPool` |
| 任务状态查询（排队中/处理中/完成/失败），完成后可取文档信息 | `GET /tasks/{taskId}`，`TaskStatus` + `TaskResponse.document` |
| 检索：同样向量化、相似度、Top-K 降序、同步接口 | `POST /search`，余弦相似度，Guava `Comparators.greatest` |
| 过滤失效文档、支持渠道筛选 | `DocumentRepository.findSearchableVectors(channel)` |
| 主动标记文档失效 | `POST /documents/{id}/invalidate` |
| 文档列表与详情（入库时间、完成时间、向量就绪、失效状态） | `GET /documents`、`GET /documents/{id}` |
| 元数据与命中计数（渠道默认 `default`，检索时更新） | `Document.hitCount`，原子 SQL 自增；`GET /stats` |
| 多线程并发安全 | 条件更新 + 原子自增 + 乐观锁，见「Concurrency safety」 |
| 统一错误响应 | `ApiExceptionHandler` 继承 `ResponseEntityExceptionHandler`，框架异常与业务异常同一响应体 |
| Java 17+ / Spring / Guava / commons-lang3 | Spring Boot 3.5，Java 17 目标 |
| H2 文件持久化 + DDL 随项目提供、启动自动建表 | `jdbc:h2:file:./data/vector-search`，`schema.sql` |
| 队列不引入中间件，用 Java 实现 | `LinkedBlockingQueue` + 自建工作线程池 |
