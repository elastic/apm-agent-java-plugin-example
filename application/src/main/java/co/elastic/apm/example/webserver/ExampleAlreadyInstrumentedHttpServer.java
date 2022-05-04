package co.elastic.apm.example.webserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * This uses the HttpServer embedded in the JDK. This HTTP server is already
 * instrumented by the Elastic Java agent in the apm-jdk-httpserver-plugin at
 * https://github.com/elastic/apm-agent-java/tree/master/apm-agent-plugins/apm-jdk-httpserver-plugin
 * <p>
 * This class is only here as a reference for you to compare agent internal
 * instrumentation against the instrumentation implemented here for the
 * other `ExampleHttpServer`
 */
public class ExampleAlreadyInstrumentedHttpServer implements ExampleHttpServer {
    private static volatile HttpServer TheServerInstance;
    private static String TheServerRootPage;
    private HttpServer thisServer;

    @Override
    public int getLocalPort() {
        return TheServerInstance == null ? -1 : TheServerInstance.getAddress().getPort();
    }

    @Override
    public synchronized void start() throws IOException {
        if (TheServerInstance != null) {
            throw new IOException("ExampleHttpServer: Ooops, you can't start this instance more than once");
        }
        InetSocketAddress addr = new InetSocketAddress("0.0.0.0", 0);
        thisServer = HttpServer.create(addr, 10);
        MyHttpHandler[] handlers = new MyHttpHandler[]{
                new ExitHandler(), new RootHandler(), //order matters
        };
        StringBuffer sb = new StringBuffer();
        for (MyHttpHandler httpHandler : handlers) {
            sb.append("<A HREF=\"")
                    .append(httpHandler.getContext())
                    .append("\">")
                    .append(httpHandler.getContext().substring(1))
                    .append("</A><BR>");
            thisServer.createContext(httpHandler.getContext(), httpHandler);
        }
        TheServerRootPage = sb.toString();
        System.out.println("ExampleAlreadyInstrumentedHttpServer: Starting new webservice on port " + thisServer.getAddress().getPort());
        thisServer.start();
        TheServerInstance = thisServer;
    }

    public void stop() {
        thisServer.stop(1);
        try {
            Thread.sleep(2000L);
        } catch (InterruptedException e1) {
        }
        TheServerInstance = null;
    }

    @Override
    public void blockUntilReady() {
        while (TheServerInstance == null) {
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                // do nothing, just enter the next sleep
            }
        }
    }

    @Override
    public void blockUntilStopped() {
        while (TheServerInstance != null) {
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                // do nothing, just enter the next sleep
            }
        }
    }

    abstract static class MyHttpHandler implements HttpHandler {
        public abstract String getContext();

        public abstract void myHandle(HttpExchange t) throws Exception;

        public void handle(HttpExchange t) {
            try {
                myHandle(t);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public static class RootHandler extends MyHttpHandler {
        @Override
        public String getContext() {
            return "/";
        }

        public void myHandle(HttpExchange t) throws IOException {
            String response = TheServerRootPage;
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    public static class ExitHandler extends MyHttpHandler {

        private static final int STOP_TIME = 3;

        @Override
        public String getContext() {
            return "/exit";
        }

        @Override
        public void myHandle(HttpExchange t) throws IOException {
            String response = TheServerRootPage;
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
            TheServerInstance.stop(STOP_TIME);
            TheServerInstance = null;
        }
    }

}
