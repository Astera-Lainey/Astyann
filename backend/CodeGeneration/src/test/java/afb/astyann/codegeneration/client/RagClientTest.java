package afb.astyann.codegeneration.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The RAG endpoint binds its result size as {@code topK} and ignores anything else, so a client
 * that sends a differently-named parameter still gets a 200 back — just always the endpoint's
 * default number of passages, with {@code codegen.ai.rag.top-k} quietly doing nothing. Nothing
 * fails loudly enough to notice, so the query string is pinned here instead.
 */
class RagClientTest {

    private HttpServer server;
    private final AtomicReference<String> receivedQuery = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/rag/context", exchange -> {
            // Raw, not getQuery() — that decodes, which would hide exactly the encoding bugs
            // these tests exist to catch.
            receivedQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = "retrieved passages".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain;charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private RagClient client() {
        return new RagClient("http://localhost:" + server.getAddress().getPort());
    }

    @Test
    void sendsTheResultSizeUnderTheParameterNameTheEndpointActuallyBinds() {
        UUID projectId = UUID.randomUUID();

        String context = client().getContext(projectId, "how is stock reserved", 12);

        assertThat(context).isEqualTo("retrieved passages");
        assertThat(receivedQuery.get())
                .contains("topK=12")
                .contains("projectId=" + projectId)
                // The old name bound to nothing on the server side.
                .doesNotContain("k=12&")
                .doesNotContain("&k=12");
    }

    @Test
    void urlEncodesQueriesContainingSpacesAndPunctuation() {
        // Module prompts are used verbatim as the query, and they are full sentences.
        client().getContext(UUID.randomUUID(), "reserve & release stock?", 5);

        assertThat(receivedQuery.get()).contains("query=reserve%20%26%20release%20stock?");
    }
}
