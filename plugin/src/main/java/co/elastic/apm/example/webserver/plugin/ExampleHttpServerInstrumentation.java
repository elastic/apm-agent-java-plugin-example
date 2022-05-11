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

/**
 * This class basically implements that: if the class
 * `co.elastic.apm.example.webserver.ExampleBasicHttpServer`
 * gets loaded, and it has any methods matching the signature
 * `handleRequest(String,x,y)` (return type doesn't matter,
 * nor do the types of the 2nd and 3rd argument), then it
 * will instrument that `handleRequest` method to add a Span
 * which starts at method entry and finishes at method exit.
 *
 * The Elastic Java agent finds this class as follows:
 * 1. It looks for the `plugins` directory which is either
 * 1a. in the agent home directory (where the agent jar file was placed), or
 * 1b. https://www.elastic.co/guide/en/apm/agent/java/current/config-core.html#config-plugins-dir
 * 2. Every jar in the `plugins` directory is scanned for the file
 * 2a. META-INF/services/co.elastic.apm.agent.sdk.ElasticApmInstrumentation
 * 3. If that file exists, then each line in the file is assumed to be a
 *    fully qualified classname subclassed from ElasticApmInstrumentation
 *    and present in the jar
 * 4. Each such class is loaded and used to instrument the loaded code
 */
public class ExampleHttpServerInstrumentation extends ElasticApmInstrumentation {

    /**
     * This instrumentation will get triggered when both the Elastic Java agent
     *  is loaded, and the class `ExampleBasicHttpServer` is loaded
     * (ie the class could load before the agent, agent before class, and
     * this instrumentation still gets triggered when they are both loaded).
     *
     * This implementation looks only for this class name, but you can
     * be much more flexible, looking for subclasses, or interface implementations
     * or even multiple alternatives of these, and other things too
     */
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.example.webserver.ExampleBasicHttpServer");
    }

    /**
     * This looks for the method signature `handleRequest(String,x,y)`
     * for the classes identified by {@code getTypeMatcher}. You could also
     * specify the return type and other aspects of the method signature
     */
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handleRequest").and(takesArguments(3))
                .and(takesArgument(0, named("java.lang.String")));
    }

    /**
     * This name `elastic-plugin-example` can be used to disable the instrumentation using
     * https://www.elastic.co/guide/en/apm/agent/java/current/config-core.html#config-disable-instrumentations
     *
     * @return A list of String names that can be used to disable this instrumentation
     */
    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("elastic-plugin-example");
    }

    /**
     * It's not necessary to override this, as the default implementation is to
     * return this instrumentation class name with "$AdviceClass" appended. But I've
     * implemented this override so that it's obvious here how the instrumentation
     * joins up with the advice inner class below
     *
     * @return the name of the Advice class that implements the instrumentation
     *         for the class+methods matched by this Instrumentation class
     */
    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.example.webserver.plugin.ExampleHttpServerInstrumentation$AdviceClass";
    }

    /**
     * The advice class is applied when the instrumentation identifies
     * it needs to be applied, ie when the above matchers ({@code getTypeMatcher}
     * and {@code getMethodMatcher}) have been matched
     *
     * The OpenTelemetry pattern for wrapping a method in a span is
     * <pre>
     *   Span span = tracer.spanBuilder(spanName).startSpan();
     *   try (Scope scope = span.makeCurrent()) {
     *     methodBeingWrapped(...);
     *   } catch(Throwable thrown){
     *     span.setStatus(StatusCode.ERROR);
     *     span.recordException(thrown);
     *     throw t;
     *   } finally {
     *     span.end();
     *   }
     * </pre>
     *
     * The way we instrument to get that is to break up the code into the
     * part before the method being called, and the part after the method is
     * finished. Then we have the first run at method entry, and the latter
     * at method exit.
     */
    public static class AdviceClass {
        /**
         * At method entry we want to create & start the Span, and make the scope current.
         * We use the ByteBuddy advice annotation `OnMethodEnter` to say this method
         * is executed at method entry.
         * <ul>
         *     <li>the method must have a `public static` signature
         *     <li>the method can return an object or be void
         *     <li>the method name (here `onEnterHandle`) can be any valid method name
         *     <li>`@Advice.Argument(0) String requestLine` lets us use `requestLine` holding
         *     the value of the first String parameter of ExampleBasicHttpServer.handleRequest()
         *     <li>`suppress` means that if any Throwable exception is thrown while the method runs,
         *     that exception will be suppressed (not thrown by ExampleBasicHttpServer.handleRequest()
         *     nor make it exit early)
         *     <li>`inline` `false` means that the code in `onEnterHandle()` will not be inlined into
         *     `ExampleBasicHttpServer.handleRequest()`, instead `ExampleHttpServerInstrumentation$AdviceClass.onEnterHandle()`
         *     will be called on entry of `ExampleBasicHttpServer.handleRequest()`
         * </ul>
         *
         * @return the Scope object so that the `OnMethodExit` {@code onExitHandle} method can get that
         *         object and close it. This is best practice for scope handling
         */
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnterHandle(@Advice.Argument(0) String requestLine) {
            //ExampleBasicHttpServer.handleRequest() has the full HTTP line for a request
            //so first we'll strip off the part after the URI request path, ie
            // requestLine = "GET /something?y#x HTTP/1.1"
            // fullRequest = "GET /something?y#x"
            String fullRequest = requestLine.substring(0, requestLine.indexOf(" HTTP"));
            //We'll just use the base URI path for the name, ie
            // fullRequest = "GET /something?y#x"
            // request = "GET /something"
            // this is so that we have a lower cardinality name, essential
            // for good indexing and composition
            String request = basicRequestPath(fullRequest);
            //Support ignoring some subset of requests
            if (shouldIgnoreThisRequest(request)) {
                return null;
            }
            // This is the recommended way to obtain the tracer with the Elastic OpenTelemetry bridge
            Tracer tracer = GlobalOpenTelemetry.get().getTracer("ExampleHttpServer");
            Span span = tracer.spanBuilder(request).setSpanKind(SpanKind.SERVER).startSpan();
            //return the scope object so that it can be closed in the OnMethodExit method
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

        /**
         * A more complete implementation would ignore the request if it
         * is a type that the various configuration options say to ignore:
         * https://www.elastic.co/guide/en/apm/agent/java/current/config-http.html#config-transaction-ignore-urls
         * https://www.elastic.co/guide/en/apm/agent/java/current/config-http.html#config-transaction-ignore-user-agents
         *
         * @param request the HTTP request being processed
         * @return false if this request should be traced, otherwise true
         */
        private static boolean shouldIgnoreThisRequest(String request) {
            return false;
        }

        /**
         * At method exit we want to end the Span, capture any exception, and close the scope.
         * We use the ByteBuddy advice annotation `OnMethodExit` to say that this method
         * will be executed at method exit.
         * <ul>
         *     <li>the method must have a `public static` signature
         *     <li>the method can return an object or be void
         *     <li>the method name (here `onExitHandle`) can be any valid method name
         *     <li>`(@Advice.Thrown Throwable thrown` lets us use `thrown` as the value
         *     of any exception thrown by `ExampleBasicHttpServer.handleRequest()` - it
         *     has a null value if no exception was thrown
         *     <li>`@Advice.Enter Object scopeObject` lets us use `scopeObject` holding
         *     the value of the Scope object returned from the {@code onEnterHandle} method
         *     <li>`suppress` means that if any Throwable exception is thrown while the method runs,
         *     that exception will be suppressed (not thrown by `ExampleBasicHttpServer.handleRequest()`
         *     nor make it exit early)
         *     <li>`onThrowable` tells ByteBuddy that we want this onExitHandle() method to be called
         *     even if any Throwable is thrown by the `ExampleBasicHttpServer.handleRequest()`
         *     <li>`inline` `false` means that the code in `onEnterHandle()` will not be inlined into
         *     `ExampleBasicHttpServer.handleRequest()`, instead `ExampleHttpServerInstrumentation$AdviceClass.onEnterHandle()`
         *     will be called on entry of `ExampleBasicHttpServer.handleRequest()`
         * </ul>
         *
         * @param thrown - any exception thrown from `ExampleBasicHttpServer.handleRequest()`
         * @param scopeObject - the Scope object returned from {@code onEnterHandle}
         */
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExitHandle(@Advice.Thrown Throwable thrown, @Advice.Enter Object scopeObject) {
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
