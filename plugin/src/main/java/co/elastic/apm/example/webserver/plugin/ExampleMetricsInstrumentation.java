package co.elastic.apm.example.webserver.plugin;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ExampleMetricsInstrumentation extends ElasticApmInstrumentation {
    //For detailed documentation of these 3 methods, see the
    // package adjacent ExampleHttpServerInstrumentation class
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

    public static class AdviceClass {
        private static volatile boolean metricWasAdded = false;
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnterHandle() {
            if (!metricWasAdded) {
                Metrics.addRegistry(new SimpleMeterRegistry());
                metricWasAdded = true;
            }
            Metrics.counter("page_counter").increment();
        }
    }
}
