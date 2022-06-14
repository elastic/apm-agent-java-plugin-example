# apm-agent-java-plugin-example
Example of instrumentation which plugs in to the Elastic Java Agent and instruments an application

## Overview

The [Elastic APM Java Agent](https://github.com/elastic/apm-agent-java/) is a Java agent that automatically measures the performance of your application and tracks errors. [Full documentation is available](https://www.elastic.co/guide/en/apm/agent/java/current/intro.html).

This project provides a detailed example of using the Elastic APM Java Plugin API to add custom instrumentation in to the agent, which the agent will automatically apply.

Detailed articles on [creating the instrumentation](https://www.elastic.co/blog/create-your-own-instrumentation-with-the-java-agent-plugin) and on [regression testing it](https://www.elastic.co/blog/create-your-own-instrumentation-with-the-java-agent-plugin) are available

## Sub-projects

This project has two sub-projects

* application - a standalone runnable example application that is here purely to provide an example target to instrument  
* plugin - the plugin that instruments the application

## Application sub-project

The application consists of a webserver and a client that executes some requests against the webserver. A [webserver interface](blob/main/application/src/main/java/co/elastic/apm/example/webserver/ExampleHttpServer.java) and two implementations are provided:

* [ExampleAlreadyInstrumentedHttpServer](blob/main/application/src/main/java/co/elastic/apm/example/webserver/ExampleAlreadyInstrumentedHttpServer.java) uses the com.sun.net.httpserver.HttpServer that is a standard part of the JDK to implement the webserver interface; the Elastic APM Java Agent already automatically instruments this technology, so this implementation is provided as a reference for checking logging and output
* [ExampleBasicHttpServer](blob/main/application/src/main/java/co/elastic/apm/example/webserver/ExampleBasicHttpServer.java) implements a very restricted custom webserver, to provide a target for the custom instrumentation

The [ExampleClient](blob/main/application/src/main/java/co/elastic/apm/example/webserver/ExampleClient.java) provides an application entry point that, when run, will start the selected webserver (chosen by setting the property `elastic.apm.service_name`), and send it some requests before terminating.

Note the application implementation is deliberately simple (eg System.out instead of a logging framework) to keep it as easy to understand as possible.

## Plugin sub-project

The plugin consists of a [single file](blob/main/plugin/src/main/java/co/elastic/apm/example/webserver/plugin/ExampleHttpServerInstrumentation.java) holding the custom instrumentation, several classes for regression testing, and a pom that builds the correct plugin jar. The details of the plugin project are explained in the articles [creating the instrumentation](https://www.elastic.co/blog/create-your-own-instrumentation-with-the-java-agent-plugin) and [regression testing it](https://www.elastic.co/blog/create-your-own-instrumentation-with-the-java-agent-plugin).

## Building

The full project can be built by cloning to your local system, changing to the root directory, and running `mvn clean install`.

Each sub-project can also be separately built the same way (changing to the sub-project root directory and running `mvn clean install`).

## Running

You need an Elastic APM Java Agent jar (the latest version is recommended, but at least version 1.31.0). Additionally an Elastic APM server is recommended, though not required (communications to the server will be dropped if it's unavailable).

### Standalone Application, no agent, no plugin

You can run the standalone application, *with no agent*, from the project root directory as follows

```aidl
java -Delastic.apm.service_name=ExampleClient-ExampleAlreadyInstrumentedHttpServer -cp application/target/application-*.jar co.elastic.apm.example.webserver.ExampleClient
java -Delastic.apm.service_name=ExampleClient-ExampleBasicHttpServer -cp application/target/application-*.jar co.elastic.apm.example.webserver.ExampleClient
```

### Application with agent, no plugin

Assuming you have set the jar location in `AGENT_JAR` and if available the APM server is specified in `APM_SERVER_URL` and `APM_SECRET_TOKEN`, you can run the application with agent *but no plugin*, from the project root directory, as follows

```aidl
export AGENT_JAR=...
export APM_SERVER_URL=...
export APM_SECRET_TOKEN=...

java -Delastic.apm.server_url=$APM_SERVER_URL -Delastic.apm.secret_token=$APM_SECRET_TOKEN -javaagent:$AGENT_JAR -Delastic.apm.log_level=DEBUG -Delastic.apm.service_name=ExampleClient-ExampleAlreadyInstrumentedHttpServer -cp application/target/application-*.jar co.elastic.apm.example.webserver.ExampleClient
java -Delastic.apm.server_url=$APM_SERVER_URL -Delastic.apm.secret_token=$APM_SECRET_TOKEN -javaagent:$AGENT_JAR -Delastic.apm.log_level=DEBUG -Delastic.apm.service_name=ExampleClient-ExampleBasicHttpServer -cp application/target/application-*.jar co.elastic.apm.example.webserver.ExampleClient
```

### Application with agent and plugin

Assuming you have set the jar location in `AGENT_JAR` and if available the APM server is specified in `APM_SERVER_URL` and `APM_SECRET_TOKEN`, you can run the application with agent and plugin, from the project root directory, as follows

```aidl
export AGENT_JAR=...
export APM_SERVER_URL=...
export APM_SECRET_TOKEN=...

java -Delastic.apm.enable_experimental_instrumentations=true -Delastic.apm.plugins_dir=plugin/target -Delastic.apm.server_url=$APM_SERVER_URL -Delastic.apm.secret_token=$APM_SECRET_TOKEN -javaagent:$AGENT_JAR -Delastic.apm.log_level=DEBUG -Delastic.apm.service_name=ExampleClient-ExampleAlreadyInstrumentedHttpServer -cp application/target/application-*.jar co.elastic.apm.example.webserver.ExampleClient
java -Delastic.apm.enable_experimental_instrumentations=true -Delastic.apm.plugins_dir=plugin/target -Delastic.apm.server_url=$APM_SERVER_URL -Delastic.apm.secret_token=$APM_SECRET_TOKEN -javaagent:$AGENT_JAR -Delastic.apm.log_level=DEBUG -Delastic.apm.service_name=ExampleClient-ExampleBasicHttpServer -cp application/target/application-*.jar co.elastic.apm.example.webserver.ExampleClient
```
