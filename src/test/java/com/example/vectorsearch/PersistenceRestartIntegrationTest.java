package com.example.vectorsearch;

import com.example.vectorsearch.api.dto.DocumentResponse;
import com.example.vectorsearch.api.dto.SearchResponse;
import com.example.vectorsearch.api.dto.SubmitDocumentResponse;
import com.example.vectorsearch.api.dto.TaskResponse;
import com.example.vectorsearch.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2 runs in file mode, so a restart must not cost us the corpus. The service is genuinely started
 * twice against the same database file, with the schema applied both times.
 */
class PersistenceRestartIntegrationTest {

    @TempDir
    static Path dataDirectory;

    @Test
    void keepsDocumentsAndTheirVectorsAcrossARestart() {
        String jdbcUrl = "jdbc:h2:file:" + dataDirectory.resolve("vector-search") + ";DB_CLOSE_DELAY=0";

        try (ConfigurableApplicationContext firstRun = start(jdbcUrl)) {
            TestRestTemplate rest = clientFor(firstRun);
            SubmitDocumentResponse accepted = rest.postForObject("/api/v1/documents",
                    Map.of("documentId", "durable", "content", "content that outlives the process",
                            "channel", "persistence"),
                    SubmitDocumentResponse.class);

            assertThat(awaitCompletion(rest, accepted.taskId()).status()).isEqualTo(TaskStatus.SUCCEEDED);
        }

        try (ConfigurableApplicationContext secondRun = start(jdbcUrl)) {
            TestRestTemplate rest = clientFor(secondRun);

            DocumentResponse document = rest.getForObject(
                    "/api/v1/documents/{id}", DocumentResponse.class, "durable");
            assertThat(document.vectorReady()).isTrue();
            assertThat(document.content()).isEqualTo("content that outlives the process");

            SearchResponse response = rest.postForObject("/api/v1/search",
                    Map.of("query", "content that outlives the process", "topK", 5, "channel", "persistence"),
                    SearchResponse.class);
            assertThat(response.results()).singleElement()
                    .satisfies(hit -> assertThat(hit.documentId()).isEqualTo("durable"));
        }
    }

    private static ConfigurableApplicationContext start(String jdbcUrl) {
        return new SpringApplicationBuilder(VectorSearchApplication.class)
                .properties(
                        "server.port=0",
                        "spring.datasource.url=" + jdbcUrl,
                        "vector.vectorization.simulated-cost-millis=0",
                        "logging.level.com.example.vectorsearch=WARN")
                .run();
    }

    private static TestRestTemplate clientFor(ConfigurableApplicationContext context) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        TestRestTemplate rest = new TestRestTemplate();
        rest.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:" + port));
        return rest;
    }

    private static TaskResponse awaitCompletion(TestRestTemplate rest, String taskId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            TaskResponse task = rest.getForObject("/api/v1/tasks/{taskId}", TaskResponse.class, taskId);
            if (task != null && !TaskStatus.IN_FLIGHT.contains(task.status())) {
                return task;
            }
            sleep();
        }
        throw new AssertionError("task " + taskId + " never reached a terminal state");
    }

    private static void sleep() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
