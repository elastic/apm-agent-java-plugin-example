package co.elastic.apm.example.webserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Really basic single-threaded HTTP server only really useful for
 * just this example as an HTTP server to instrument. Don't use this
 * for anything else is my advice. Can only handle 1 connection at
 * a time, the start() call is blocking, doesn't keep-alive, no SSL,
 * no compression, only returns a hardcoded page, will break on
 * large requests, so incredibly limited!
 * <p>
 * It's also really verbose and uses System.out instead of logging
 * <p>
 * Calling /exit as the URL path will terminate it, any other path
 * returns the same page in `TheHtmlPage`
 */
public class ExampleBasicHttpServer implements ExampleHttpServer {
    private static final String TheHttpHeader = "HTTP/1.0 200 OK\nContent-Type: text/html; charset=utf-8\nServer: ExampleHttpServer\n\n";
    private static final String TheHtmlPage = "<HTML><HEAD><TITLE>ExampleHttpServer</TITLE></HEAD><BODY>Nothing Here</BODY></HTML>";

    private volatile ServerSocket server;
    private volatile boolean isReady = false;

    @Override
    public void blockUntilReady() {
        while (!this.isReady) {
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                // do nothing, just enter the next sleep
            }
        }
    }

    @Override
    public void blockUntilStopped() {
        while (this.server != null && !this.server.isClosed()) {
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                // do nothing, just enter the next sleep
            }
        }
    }

    @Override
    public void stop() {
        try {
            if (this.server != null) {
                System.out.println("ExampleHttpServer: Attempting to call stop()");
                this.server.close();
                System.out.println("ExampleHttpServer: Successfully called stop()");
            } else {
                System.out.println("ExampleHttpServer: Attempted to call stop() on a server that was never start() successfully!");
            }
        } catch (IOException e) {
            System.out.println("ExampleHttpServer: Unsuccessfully called stop(), stack trace follows, error is:" + e.getLocalizedMessage());
            e.printStackTrace(System.out);
        }
    }

    @Override
    public synchronized void start() throws IOException {
        System.out.println("ExampleHttpServer: Attempting to call start()");
        if (this.server != null) {
            throw new IOException("ExampleHttpServer: Ooops, you can't start this instance more than once");
        }
        this.server = new ServerSocket(0);
        System.out.println("ExampleHttpServer: Successfully called start(), now listening for requests");
        boolean keepGoing = true;
        while (keepGoing) {
            this.isReady = true;
            try (Socket client = this.server.accept()) {
                keepGoing = processClient(client);
            }
        }
        stop();
    }

    @Override
    public int getLocalPort() {
        return this.server == null ? -1 : this.server.getLocalPort();
    }

    private boolean processClient(Socket client) {
        boolean keepGoing = true;
        System.out.println("ExampleHttpServer: Received a client connection, now attempting to read the request");
        while (!client.isClosed() && !client.isInputShutdown() && !client.isOutputShutdown()) {
            try (BufferedReader clientInput = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
                String line = clientInput.readLine();
                if (line == null) {
                    //hmmm, try again
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                    }
                    line = clientInput.readLine();
                    if (line == null) {
                        clientInput.close();
                        break;
                    }
                }
                if (line.startsWith("GET /exit")) {
                    keepGoing = false;
                }
                PrintWriter outputToClient = new PrintWriter(client.getOutputStream());
                handleRequest(line, clientInput, outputToClient);
                clientInput.close();
                outputToClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return keepGoing;
    }

    private void handleRequest(String request, BufferedReader clientInput, PrintWriter outputToClient) throws IOException {
        System.out.println("ExampleHttpServer: HTTP-HEADER: " + request);
        String line;
        while ((line = clientInput.readLine()) != null && line.length() != 0) {
            System.out.println("ExampleHttpServer: HTTP-HEADER: " + line);
        }
        System.out.println("ExampleHttpServer: Now replying the standard page and terminating the connection");
        outputToClient.println(TheHttpHeader);
        outputToClient.println(TheHtmlPage);
        outputToClient.flush();
    }
}
