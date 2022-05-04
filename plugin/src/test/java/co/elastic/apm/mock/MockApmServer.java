package co.elastic.apm.mock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * This is a server which just accepts lines of JSON code and if the JSON
 * is valid and the root node is "transaction", then adds that JSON object
 * to a transaction list which is accessible externally to the class.
 *
 * The Elastic agent sends lines of JSON code, and so this mock server
 * can be used as a basic APM server for testing.
 *
 * The HTTP server used is the JDK embedded com.sun.net.httpserver
 */
public class MockApmServer {
    /**
     * Simple main that starts a mock APM server, prints the port it is
     * running on, and exits after 2_000 seconds. This is not needed
     * for testing, it is just a convenient template for trying things out
     * if you want play around.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        MockApmServer server = new MockApmServer();
        System.out.println(server.start());
        server.blockUntilReady();
        Thread.sleep(2_000_000L);
        server.stop();
        server.blockUntilStopped();
    }

    private static volatile HttpServer TheServerInstance;

    private final List<JsonNode> transactions = new ArrayList<>();

    /**
     * A count of the number of transactions received and not yet removed
     * @return the number of transactions received and not yet removed
     */
    public int getTransactionCount() {
        synchronized (transactions) {
            return transactions.size();
        }
    }

    /**
     * Get's the transaction at index i if it exists within the timeout
     * specified. If it doesn't exist within the timeout period, an
     * IllegalArgumentException is thrown
     * @param i - the index to retrieve a transaction from
     * @param timeOutInMillis - millisecond timeout to wait for the
     *                        transaction at index i to exist
     * @return - the transaction information as a JSON object
     * @throws IllegalArgumentException - thrown if no transaction
     *                         exists at index i at timeout
     */
    public JsonNode getTransaction(int i, long timeOutInMillis) {
        //because the agent writes to the server asynchronously,
        //any transaction created in a client is not here immediately
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeOutInMillis) {
            synchronized (transactions) {
                if (transactions.size() > i) {
                    break;
                }
            }
            try {Thread.sleep(1);} catch (InterruptedException e) {e.printStackTrace();}
        }
        synchronized (transactions) {
            if (transactions.size() <= i) {
                throw new IllegalArgumentException("The apm server does not have a transaction at index " + i);
            }
        }
        synchronized (transactions) {
            return transactions.remove(i);
        }
    }

    /**
     * Start the Mock APM server. Just returns empty JSON structures for every incoming message
     * @return - the port the Mock APM server started on
     * @throws IOException
     */
    public synchronized int start() throws IOException {
        if (TheServerInstance != null) {
            throw new IOException("MockApmServer: Ooops, you can't start this instance more than once");
        }
        InetSocketAddress addr = new InetSocketAddress("0.0.0.0", 0);
        HttpServer server = HttpServer.create(addr, 10);
        server.createContext("/exit", new ExitHandler());
        server.createContext("/", new RootHandler());

        server.start();
        TheServerInstance = server;
        return server.getAddress().getPort();
    }

    /**
     * Stop the server gracefully if possible
     */
    public synchronized void stop() {
        TheServerInstance.stop(1);
        TheServerInstance = null;
    }

    class RootHandler implements HttpHandler {
        public void handle(HttpExchange t) {
            try {
                InputStream body = t.getRequestBody();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8*1024];
                int lengthRead;
                while((lengthRead = body.read(buffer)) > 0) {
                    bytes.write(buffer, 0, lengthRead);
                }
                reportTransactions(bytes.toString());
                String response = "{}";
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void reportTransactions(String json) {
            String[] lines = json.split("[\r\n]");
            for (String line: lines) {
                reportTransaction(line);
            }
        }
        private void reportTransaction(String line) {
            System.out.println("MockApmServer reading JSON objects: "+ line);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode messageRootNode = null;
            try {
                messageRootNode = objectMapper.readTree(line);
                JsonNode transactionNode = messageRootNode.get("transaction");
                if (transactionNode != null) {
                    synchronized (transactions) {
                        transactions.add(transactionNode);
                    }
                }
            } catch (JsonProcessingException e) {
                System.out.println("Not JSON: "+line);
                e.printStackTrace();
            }
        }
    }

    static class ExitHandler implements HttpHandler {
        private static final int STOP_TIME = 3;

        public void handle(HttpExchange t) {
            try {
                InputStream body = t.getRequestBody();
                String response = "{}";
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
                TheServerInstance.stop(STOP_TIME);
                TheServerInstance = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Wait until the server is ready to accept messages
     */
    public void blockUntilReady() {
        while (TheServerInstance == null) {
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                // do nothing, just enter the next sleep
            }
        }
    }

    /**
     * Wait until the server is terminated
     */
    public void blockUntilStopped() {
        while (TheServerInstance != null) {
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                // do nothing, just enter the next sleep
            }
        }
    }

    /*

        @Override
    }

    }

    private void report(String line) throws JsonMappingException, JsonProcessingException {
        System.out.println(line);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode messageRootNode = objectMapper.readTree(line);
        JsonNode metadataNode = messageRootNode.get("metadata");
        JsonNode serviceNode = messageRootNode.get("service");
        if (metadataNode != null && serviceNode == null) {
            serviceNode = metadataNode.get("service");
        }
        if (serviceNode != null) {
            reportService(serviceNode);
            return;
        }
        JsonNode metricNode = messageRootNode.get("metricset");
        if (metricNode != null) {
            reportMetrics(metricNode);
            return;
        }
        JsonNode transactionNode = messageRootNode.get("transaction");
        if (transactionNode != null) {
            reportTransaction(transactionNode);
            return;
        }
        JsonNode spanNode = messageRootNode.get("span");
        if (spanNode != null) {
            reportSpan(spanNode);
            return;
        }

        System.out.println("UNKNOWN TYPE: "+line);

    }

    private void reportSpan(JsonNode spanNode) {
        String name = spanNode.get("name").asText();
        String duration = spanNode.get("duration").asText();
        System.out.println("Span: "+name+ " "+duration+"ms "+spanNode);
    }

    private void reportTransaction(JsonNode transactionNode) {
        String name = transactionNode.get("name").asText();
        String duration = transactionNode.get("duration").asText();
        System.out.println("Transaction: "+name+ " "+duration+"ms "+transactionNode);
    }

    private void reportMetrics(JsonNode metricNode) {
        if (metricNode.get("samples") != null) {
            //ignoring samples
        } else {
            System.out.println("UNKNOWN METRIC TYPE: "+metricNode);
        }
    }

    private void reportService(JsonNode serviceNode) {
        String name = serviceNode.get("name").asText();
        System.out.println("Service: "+name);
    }

 */
}
