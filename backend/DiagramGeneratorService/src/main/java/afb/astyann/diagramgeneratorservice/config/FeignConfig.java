package afb.astyann.diagramgeneratorservice.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.http.converter.autoconfigure.ClientHttpMessageConvertersCustomizer;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    /**
     * Builds Feign's shared message-converter list eagerly, at startup, on a single thread.
     *
     * <p>Spring Cloud's own {@code FeignHttpMessageConverters} builds that list lazily on the
     * first call, from a plain (non-volatile, unsynchronized) field:
     *
     * <pre>
     * if (this.converters == null) {
     *     this.converters = new ArrayList&lt;&gt;();   // published immediately, still empty
     *     ... converters are added one by one ...
     * }
     * </pre>
     *
     * <p>{@code DiagramGenerationService.startGeneration} fans every requested diagram type out
     * onto {@code diagramExecutor} at once, and each task calls {@code aiServiceClient} over
     * Feign. So several threads reach that lazy init simultaneously on a cold client: one wins the
     * null check and starts filling the list while the others already see it non-null and hand it
     * straight to {@code HttpMessageConverterExtractor}, whose constructor asserts
     *
     * <ul>
     *   <li>{@code 'messageConverters' must not be empty} — the list was still empty, and</li>
     *   <li>{@code 'messageConverters' must not contain null elements} — {@code ArrayList.add}
     *       increments {@code size} and writes the slot without any happens-before edge, so a
     *       racing reader can observe the larger size before the element itself.</li>
     * </ul>
     *
     * <p>Both surface as an intermittent generation failure that depends purely on thread timing.
     * Declaring the bean here fixes it for every Feign client in this service, because
     * {@code FeignClientsConfiguration}'s own definition is {@code @ConditionalOnMissingBean} with
     * the default ancestor-searching strategy, so each per-client child context backs off and
     * reuses this one instead of building its own.
     *
     * <p>The {@code getConverters()} call below is the whole point: it forces the lazy
     * initialisation to happen here, during context refresh, before any request thread exists.
     *
     * <p>Note this moves the bean from Feign's per-client child contexts up into the application
     * context, so a {@link HttpMessageConverterCustomizer} scoped to a single {@code @FeignClient}
     * would no longer apply. This service defines none — customizers declared as normal beans are
     * still picked up.
     */
    @Bean
    public FeignHttpMessageConverters feignHttpMessageConverters(
            ObjectProvider<ClientHttpMessageConvertersCustomizer> customizers,
            ObjectProvider<HttpMessageConverterCustomizer> cloudCustomizers) {
        FeignHttpMessageConverters converters = new FeignHttpMessageConverters(customizers, cloudCustomizers);
        converters.getConverters();
        return converters;
    }
}