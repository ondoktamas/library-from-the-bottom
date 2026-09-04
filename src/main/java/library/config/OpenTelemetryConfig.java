package library.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wires the OpenTelemetry SDK directly (no Java agent, no external collector),
 * exporting spans and metrics to the application log so the whole pipeline is
 * visible while running locally.
 */
@Configuration
public class OpenTelemetryConfig {

    private static final String INSTRUMENTATION_NAME = "library-management-system";

    /**
     * Deliberately {@code build()} rather than {@code buildAndRegisterGlobal()}:
     * the latter writes a JVM-wide singleton that may only be set once, so a
     * second application context in the same JVM (any test needing a different
     * profile, property set, or {@code @MockBean}) would fail to start with
     * "GlobalOpenTelemetry.set has already been called". Nothing here reads
     * {@code GlobalOpenTelemetry} - the SDK is injected as a bean instead.
     *
     * <p>Declared as {@link OpenTelemetrySdk} (not the {@link OpenTelemetry}
     * interface) so Spring's inferred destroy method finds {@code close()} and
     * shuts the providers down on context close, flushing buffered spans and
     * stopping the periodic metric reader thread.
     */
    @Bean
    public OpenTelemetrySdk openTelemetry() {
        Resource resource = Resource.getDefault()
                .merge(Resource.builder().put("service.name", INSTRUMENTATION_NAME).build());

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
                .setResource(resource)
                .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(PeriodicMetricReader.builder(LoggingMetricExporter.create())
                        .setInterval(Duration.ofSeconds(10))
                        .build())
                .setResource(resource)
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .build();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(INSTRUMENTATION_NAME);
    }

    @Bean
    public Meter meter(OpenTelemetry openTelemetry) {
        return openTelemetry.getMeter(INSTRUMENTATION_NAME);
    }
}
