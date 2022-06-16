#!/bin/bash
#set AGENT_JAR to the full or relative path to the Elastic APM Java agent jar, eg
#export AGENT_JAR=../elastic-apm-agent-1.32.0.jar
if [ "x$AGENT_JAR" == "x" ]; then
  echo "ERROR: 'AGENT_JAR' env var must be set"
  exit 1
fi

echo "Run the standalone application, *with no agent*"
echo "First the ExampleAlreadyInstrumentedHttpServer"
java -Delastic.apm.service_name=ExampleClient-ExampleAlreadyInstrumentedHttpServer -cp application/target/application-*.jar co.elastic.apm.example.webserver.ExampleClient
echo "Then the uninstrumented ExampleBasicHttpServer"
java -Delastic.apm.service_name=ExampleClient-ExampleBasicHttpServer -cp application/target/application-*.jar co.elastic.apm.example.webserver.ExampleClient

echo "Run the standalone application, *with the agent but no plugin*"
echo "First the ExampleAlreadyInstrumentedHttpServer"
java -Delastic.apm.server_url=$APM_SERVER_URL -Delastic.apm.secret_token=$APM_SECRET_TOKEN -javaagent:$AGENT_JAR -Delastic.apm.log_level=DEBUG -Delastic.apm.service_name=ExampleClient-ExampleAlreadyInstrumentedHttpServer -cp application/target/application-*.jar co.elastic.apm.example.webserver.ExampleClient
echo "Then the still uninstrumented ExampleBasicHttpServer (still uninstrumented because the plugin is not present)"
java -Delastic.apm.server_url=$APM_SERVER_URL -Delastic.apm.secret_token=$APM_SECRET_TOKEN -javaagent:$AGENT_JAR -Delastic.apm.log_level=DEBUG -Delastic.apm.service_name=ExampleClient-ExampleBasicHttpServer -cp application/target/application-*.jar co.elastic.apm.example.webserver.ExampleClient

echo "Run the standalone application, *with the agent and plugin*"
echo "First the ExampleAlreadyInstrumentedHttpServer"
java -Delastic.apm.enable_experimental_instrumentations=true -Delastic.apm.plugins_dir=plugin/target -Delastic.apm.server_url=$APM_SERVER_URL -Delastic.apm.secret_token=$APM_SECRET_TOKEN -javaagent:$AGENT_JAR -Delastic.apm.log_level=DEBUG -Delastic.apm.service_name=ExampleClient-ExampleAlreadyInstrumentedHttpServer -cp application/target/application-*.jar co.elastic.apm.example.webserver.ExampleClient
echo "Then the now instrumented ExampleBasicHttpServer (instrumented by the plugin)"
java -Delastic.apm.enable_experimental_instrumentations=true -Delastic.apm.plugins_dir=plugin/target -Delastic.apm.server_url=$APM_SERVER_URL -Delastic.apm.secret_token=$APM_SECRET_TOKEN -javaagent:$AGENT_JAR -Delastic.apm.log_level=DEBUG -Delastic.apm.service_name=ExampleClient-ExampleBasicHttpServer -cp application/target/application-*.jar co.elastic.apm.example.webserver.ExampleClient
