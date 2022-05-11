package co.elastic.apm.example.webserver;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ExampleClient {
    public static int PORT;

    public static void main(String[] args) throws IOException, InterruptedException {
        // Start the server in a separate thread
        System.out.println("ExampleClient: Starting the webserver");
        ExampleHttpServer server;
        if ("ExampleClient-ExampleAlreadyInstrumentedHttpServer".equals(System.getProperty("elastic.apm.service_name"))) {
            server = new ExampleAlreadyInstrumentedHttpServer();
        } else if ("ExampleClient-ExampleBasicHttpServer".equals(System.getProperty("elastic.apm.service_name"))) {
            server = new ExampleBasicHttpServer();
        } else {
            throw new IOException("Must set -Delastic.apm.service_name=ExampleClient-ExampleBasicHttpServer or -Delastic.apm.service_name=ExampleClient-ExampleAlreadyInstrumentedHttpServer");
        }
        new Thread(() -> {startServer(server);}).start();
        System.out.println("ExampleClient: waiting for webserver to be ready");
        server.blockUntilReady();
        PORT = server.getLocalPort();
        HttpClient client = HttpClient.newHttpClient();
        executeRequest(client, "nothing");
        executeRequest(client, "nothing?withsomething=true");
        executeRequest(client, "nothing#somelink");
        executeRequest(client, "nothing#somelink?withsomething=true");
        executeRequest(client, "exit");

        System.out.println("ExampleClient: waiting for webserver terminate (or will exit in 10 seconds regardless)");
        exitIn10Seconds();
        server.blockUntilStopped();

        System.out.println("ExampleClient: Exiting");
    }

    private static void exitIn10Seconds() {
        new Thread() {
            public void run() {
                try {Thread.sleep(10_000L);} catch (InterruptedException e) {}
                System.exit(0);
            }
        }.start();
    }

    private static void executeRequest(HttpClient client, String req) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:"+PORT+"/"+req))
                .GET() // GET is default
                .build();

        System.out.println("ExampleClient: calling "+request);
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
        System.out.println("ExampleClient: call result status is "+response.statusCode());
    }

    private static void startServer(ExampleHttpServer server) {
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
