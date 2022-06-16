IF "x%AGENT_JAR%" == "x" echo "ERROR: 'AGENT_JAR' env var must be set" && pause && exit 1	

FOR %%f in (application\target\*.jar) DO set TARGET_JAR=%%f

echo "Run the standalone application, *with no agent*"
echo "First the ExampleAlreadyInstrumentedHttpServer"
java -Delastic.apm.service_name=ExampleClient-ExampleAlreadyInstrumentedHttpServer -cp %TARGET_JAR% co.elastic.apm.example.webserver.ExampleClient
echo "Then the uninstrumented ExampleBasicHttpServer"
java -Delastic.apm.service_name=ExampleClient-ExampleBasicHttpServer -cp %TARGET_JAR% co.elastic.apm.example.webserver.ExampleClient

echo "Run the standalone application, *with the agent but no plugin*"
echo "First the ExampleAlreadyInstrumentedHttpServer"
java -Delastic.apm.server_url=%APM_SERVER_URL% -Delastic.apm.secret_token=%APM_SECRET_TOKEN% -javaagent:%AGENT_JAR% -Delastic.apm.log_level=DEBUG -Delastic.apm.service_name=ExampleClient-ExampleAlreadyInstrumentedHttpServer -cp %TARGET_JAR% co.elastic.apm.example.webserver.ExampleClient
echo "Then the still uninstrumented ExampleBasicHttpServer (still uninstrumented because the plugin is not present)"
java -Delastic.apm.server_url=%APM_SERVER_URL% -Delastic.apm.secret_token=%APM_SECRET_TOKEN% -javaagent:%AGENT_JAR% -Delastic.apm.log_level=DEBUG -Delastic.apm.service_name=ExampleClient-ExampleBasicHttpServer -cp %TARGET_JAR% co.elastic.apm.example.webserver.ExampleClient

echo "Run the standalone application, *with the agent and plugin*"
echo "First the ExampleAlreadyInstrumentedHttpServer"
java -Delastic.apm.enable_experimental_instrumentations=true -Delastic.apm.plugins_dir=plugin\target -Delastic.apm.server_url=%APM_SERVER_URL% -Delastic.apm.secret_token=%APM_SECRET_TOKEN% -javaagent:%AGENT_JAR% -Delastic.apm.log_level=DEBUG -Delastic.apm.service_name=ExampleClient-ExampleAlreadyInstrumentedHttpServer -cp %TARGET_JAR% co.elastic.apm.example.webserver.ExampleClient
echo "Then the now instrumented ExampleBasicHttpServer (instrumented by the plugin)"
java -Delastic.apm.enable_experimental_instrumentations=true -Delastic.apm.plugins_dir=plugin\target -Delastic.apm.server_url=%APM_SERVER_URL% -Delastic.apm.secret_token=%APM_SECRET_TOKEN% -javaagent:%AGENT_JAR% -Delastic.apm.log_level=DEBUG -Delastic.apm.service_name=ExampleClient-ExampleBasicHttpServer -cp %TARGET_JAR% co.elastic.apm.example.webserver.ExampleClient
