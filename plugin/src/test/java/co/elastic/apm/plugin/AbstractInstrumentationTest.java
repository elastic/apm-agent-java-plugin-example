package co.elastic.apm.plugin;

import co.elastic.apm.attach.ElasticApmAttacher;
import co.elastic.apm.mock.MockApmServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AbstractInstrumentationTest {

    private static final HashMap<String, String > Properties = new HashMap<>();
    protected static final MockApmServer ApmServer = new MockApmServer();

    @BeforeAll
    public static void startApmServerAndSetAgentPropertiesAndStartAgent() throws IOException {
        //Start the mock APM server - receives transactions from the agent
        int mockApmServerPort = ApmServer.start();
        ApmServer.blockUntilReady();

        //Set properties before starting the agent
        setProperty("elastic.apm.server_url", "http://localhost:"+mockApmServerPort);
        setProperty("elastic.apm.plugins_dir", "target"); //to load the plugin
        setProperty("elastic.apm.enable_experimental_instrumentations", "true"); //need for Otel in 1.30
        setProperty("elastic.apm.api_request_size", "100b"); //flush quickly

        //Start the agent
        ElasticApmAttacher.attach();
    }

    @AfterAll
    public static void stopApmServerAndResetProperties() {
        resetProperties();
        ApmServer.stop();
    }

    public static void setProperty(String name, String value){
        synchronized (Properties) {
            if (Properties.containsKey(name)) {
                throw new IllegalStateException("Cannot redefine a property before resetting it: " + name);
            }
            String oldPropertyValue = System.getProperty(name);
            Properties.put(name, oldPropertyValue);
            System.setProperty(name, value);
        }
    }

    public static void resetProperties(){
        synchronized (Properties) {
            for (Map.Entry<String, String> entry : Properties.entrySet()) {
                if (entry.getValue() == null) {
                    System.clearProperty(entry.getKey());
                } else {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            }
            Properties.clear();
        }
    }

}
