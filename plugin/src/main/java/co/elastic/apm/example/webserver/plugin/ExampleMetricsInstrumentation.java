package co.elastic.apm.example.webserver.plugin;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * For detailed documentation of the 3 overridden methods, see the
 * ExampleHttpServerInstrumentation class in this package.
 *
 * A quick summary is that we are matching the
 * ExampleBasicHttpServer.handleRequest() method whenever
 * it gets loaded, and  we'll instrument that method using
 * the inner AdviceClass class below
 */
public class ExampleMetricsInstrumentation extends ElasticApmInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.example.webserver.ExampleBasicHttpServer");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handleRequest").and(takesArguments(3))
                .and(takesArgument(0, named("java.lang.String")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("elastic-plugin-example");
    }


    /**
     * This advice class is applied when the instrumentation identifies
     * it needs to be applied, ie when the above matchers ({@code getTypeMatcher}
     * and {@code getMethodMatcher}) have been matched
     *
     * The Elastic APM Java agent provides the OpenTelemetry metrics capability -

     * the agent fully implements the OpenTelemetry metrics framework. See
     * https://www.elastic.co/guide/en/apm/agent/java/master/opentelemetry-bridge.html#otel-metrics
     */
    public static class AdviceClass {
        /**
         * At initialization, we register the with the OpenTelemetry registry by
         * creating a meter, a page count metric (`page_views`) which will be available
         * in the Elastic APM metrics views.
         *
         * For details on the Byte Buddy advice annotation used here,
         * see the ExampleHttpServerInstrumentation$AdviceClass
         * class and it's `onEnterHandle` method javadoc, in this package.
         */
        private static volatile LongCounter pageViewCounter;

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnterHandle() {
            if (pageViewCounter == null) {
                pageViewCounter = GlobalOpenTelemetry
                        .getMeter("ExampleHttpServer")
                        .counterBuilder("page_views")
                        .setDescription("Page view count")
                        .build();
            }
            pageViewCounter.add(1);
        }
    }
}
