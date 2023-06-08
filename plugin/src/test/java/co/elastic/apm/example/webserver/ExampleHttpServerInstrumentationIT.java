package co.elastic.apm.example.webserver;

import co.elastic.apm.example.webserver.plugin.ExampleHttpServerInstrumentation;
import co.elastic.apm.plugin.AbstractInstrumentationTest;
import com.fasterxml.jackson.databind.JsonNode;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExampleHttpServerInstrumentationIT extends AbstractInstrumentationTest {

    protected static Exception START_EXCEPTION;
    protected static int PORT = -1;
    protected static ExampleBasicHttpServer Server;
    protected static HttpClient Client = HttpClient.newHttpClient();

    @BeforeAll
    public static void startServer() throws IOException {
        Server = new ExampleBasicHttpServer();
        new Thread(() -> {
            try {
                Server.start();
            } catch (Exception e) {
                START_EXCEPTION = e;
            }
        }).start();
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {Server.blockUntilReady();});
        assertTrue(START_EXCEPTION == null);
        PORT = Server.getLocalPort();
    }

    @AfterAll
    public static void stopServer() throws IOException, InterruptedException {
        assertEquals(executeRequest("exit"), 200);
        Server.stop();
        assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {Server.blockUntilStopped();});
    }

    @Test
    void testInstrumentationMakes1TransactionPerRequestWithCorrectNaming() throws IOException, InterruptedException, TimeoutException {
        Tracer elasticTracer = GlobalOpenTelemetry.get().getTracer("ExampleHttpServer");
        for (String request: List.of("nothing", "nothing?withsomething=true", "nothing#somelink", "nothing#somelink?withsomething=true")) {
            assertEquals(200, executeRequest(request));
            JsonNode transaction = ApmServer.getAndRemoveTransaction(0, 1000);
            assertEquals("GET /nothing", transaction.get("name").asText());
            assertEquals(0, ApmServer.getTransactionCount());
        }
    }

    private static int executeRequest(String req) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:"+PORT+"/"+req))
                .GET() // GET is default
                .build();

        HttpResponse<String> response = Client.send(request,
                HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    @Test
    void testInstrumentationIncrementsTheOtelPageCounterMetric() throws IOException, InterruptedException, TimeoutException {
        testInstrumentationIncrementsThePageCounterMetrics("page_views", 5000L);
    }

    @Test
    void testInstrumentationIncrementsTheMicrometerPageCounterMetric() throws IOException, InterruptedException, TimeoutException {
        //Although we've set the metrics to be sent every second, micrometer itself is not synchronous,
        //so it can take a while for micrometer to update the metric. So the test is set to run for up to 50 seconds
        testInstrumentationIncrementsThePageCounterMetrics("page_counter", 50000L);
    }

    void testInstrumentationIncrementsThePageCounterMetrics(String metricName, long timeoutInMillis) throws IOException, InterruptedException, TimeoutException {
        assertEquals(200, executeRequest("random_with_"+metricName));
        JsonNode transaction = ApmServer.getAndRemoveTransaction(0, 1000);
        assertEquals("GET /random_with_"+metricName, transaction.get("name").asText());

        JsonNode metricset = ApmServer.popMetricset(5000);
        boolean foundPageCountMetric = false;
        boolean foundNonZeroPageCountMetric = false;
        long start = System.currentTimeMillis();
        for(long now = start; metricset != null && now-start < timeoutInMillis; now = System.currentTimeMillis()) {
            if (metricset.get("samples") != null && metricset.get("samples").get(metricName) != null) {
                foundPageCountMetric = true;
                int pageCountValue = metricset.get("samples").get(metricName).get("value").intValue();
                if (pageCountValue > 0) {
                    foundNonZeroPageCountMetric = true;
                    break;
                }
            }
            metricset = ApmServer.popMetricset(5000);
        }
        assertEquals(true, foundPageCountMetric);
        assertEquals(true, foundNonZeroPageCountMetric);
    }

}
