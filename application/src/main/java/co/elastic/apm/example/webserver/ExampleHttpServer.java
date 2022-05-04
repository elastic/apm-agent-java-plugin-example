package co.elastic.apm.example.webserver;

import java.io.IOException;

public interface ExampleHttpServer {
    public void blockUntilReady();
    public void blockUntilStopped();
    public void stop();
    public void start() throws IOException;
    public int getLocalPort();
}
