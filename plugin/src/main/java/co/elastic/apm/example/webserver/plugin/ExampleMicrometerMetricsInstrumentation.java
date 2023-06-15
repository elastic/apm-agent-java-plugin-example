package co.elastic.apm.example.webserver.plugin;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.CountingMode;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.time.Duration;
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
public class ExampleMicrometerMetricsInstrumentation extends ElasticApmInstrumentation {
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
     * The ELastic APM Java agent provides a metrics capability using
     * the Micrometer framework, see
     * https://www.elastic.co/guide/en/apm/agent/java/current/metrics.html#metrics-micrometer
     */
    public static class AdviceClass {
        /**
         * At method entry, we want to ensure that we've registered the
         * with the micrometer registry (only needed to do once, so
         * it's guarded with a boolean), then we'll increment
         * a page count metric, `page_count` which will be available
         * in the Elastic APM metrics views.
         *
         * For details on the Byte Buddy advice annotation used here,
         * see the ExampleHttpServerInstrumentation$AdviceClass
         * class and it's `onEnterHandle` method javadoc, in this package.
         */
        private static volatile boolean metricWasAdded = false;
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnterHandle() {
            if (!metricWasAdded) {
                Metrics.addRegistry(new SimpleMeterRegistry(new SimpleConfig() {

                    @Override
                    public CountingMode mode() {
                        // to report the delta since the last report
                        // this makes building dashboards a bit easier
                        return CountingMode.STEP;
                    }

                    @Override
                    public Duration step() {
                        // the duration should match metrics_interval, which defaults to 30s
                        return Duration.ofSeconds(30);
                    }

                    @Override
                    public String get(String key) {
                        return null;
                    }
                }, Clock.SYSTEM));
                metricWasAdded = true;
            }
            Metrics.counter("page_counter").increment();
        }
    }
}
