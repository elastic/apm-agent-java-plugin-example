package co.elastic.apm.example.webserver.plugin;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.util.Collection;
import java.util.Collections;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

//This class basically implements that: if the class
//`co.elastic.apm.example.webserver.ExampleBasicHttpServer`
//gets loaded, and it has any methods matching the signature
//`handleRequest(String,x,y)` (return type doesn't matter,
//nor do the types of the 2nd and 3rd argument), then it
//will instrument that `handleRequest` method to add a Span
//which starts at method entry and finishes at method exit.
//
//The Elastic Java agent finds this class as follows:
//1. It looks for the `plugins` directory which is either
//1a. in the agent home directory (where the agent jar file was placed), or
//1b. https://www.elastic.co/guide/en/apm/agent/java/current/config-core.html#config-plugins-dir
//2. Any jar in the `plugins` directory is scanned for the file
//2a. META-INF/services/co.elastic.apm.agent.sdk.ElasticApmInstrumentation
//3. If that file exists, then each line in the file is assumed to be a
//   fully qualified classname subclassed from ElasticApmInstrumentation
//   and present in the jar
//4. Each such class is loaded and used to instrument the loaded code
public class ExampleHttpServerInstrumentation extends ElasticApmInstrumentation {

    //This instrumentation will get triggered when both the Elastic Java agent
    // is loaded, and the class `ExampleBasicHttpServer` is loaded
    //(ie the class could load before the agent, agent before class, and
    //this instrumentation still gets triggered when the agent is loaded).
    //
    //This implementation looks only for this class name, but you can
    //be much more flexible, looking for subclasses, or interface implementations
    //or even multiple alternatives of these, and other things too
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.example.webserver.ExampleBasicHttpServer");
    }

    //This looks for the method signature `handleRequest(String,x,y)` 
    //for the classes identified by {@code getTypeMatcher}. You could also
    //specify the return type and other aspects of the method signature
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handleRequest").and(takesArguments(3))
                .and(takesArgument(0, named("java.lang.String")));
    }

    //This name `elastic-plugin-example` can be used to disable the instrumentation using
    //https://www.elastic.co/guide/en/apm/agent/java/current/config-core.html#config-disable-instrumentations
    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("elastic-plugin-example");
    }

    //It's not necessary to override this, as the default implementation is to
    //return this instrumentation class name with "$AdviceClass" appended. But I've
    //implemented this override so that it's obvious here how the instrumentation
    //joins up with the advice inner class below
    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.example.webserver.plugin.ExampleHttpServerInstrumentation$AdviceClass";
    }

    //The advice class is applied when the instrumentation identifies
    //it needs to be applied, ie when the matches above ({@code getTypeMatcher}
    //and {@code getMethodMatcher}) have been matched
    public static class AdviceClass {
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnterHandle(@Advice.Argument(0) String requestLine) {
            String fullRequest = requestLine.substring(0, requestLine.indexOf(" HTTP"));
            //We'll just use the base URI path for the name
            //we give the span, so strip the parameters
            String request = basicRequestPath(fullRequest);
            //Support ignoring some subset of requests
            if (shouldIgnoreThisRequest(request)) {
                return null;
            }
            Tracer tracer = GlobalOpenTelemetry.get().getTracer("ExampleHttpServer");
            Span span = tracer.spanBuilder(request).setSpanKind(SpanKind.SERVER).startSpan();
            span.updateName(request);
            //return the scope object
            return span.makeCurrent();
        }

        private static String basicRequestPath(String request) {
            int index = request.indexOf("?");
            if (index > 0) {
                request = request.substring(0, index);
            }
            index = request.indexOf("#");
            if (index > 0) {
                request = request.substring(0, index);
            }
            return request;
        }

        private static boolean shouldIgnoreThisRequest(String request) {
            //A more complete implementation would ignore the request if it
            //is a type that the various configuration options say to ignore:
            //transaction_ignore_urls, transaction_ignore_user_agents, 
            return false;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Thrown Throwable thrown, @Advice.Enter Object scopeObject) {
            Span span = Span.current();
            span.end();
            if (thrown != null) {
                span.setStatus(StatusCode.ERROR);
                span.recordException(thrown);
            }
            Scope scope = (Scope) scopeObject;
            scope.close();
        }
    }
}
