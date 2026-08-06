package afb.astyann.documentservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the fix for the intermittent {@code 'messageConverters' must not contain null elements} /
 * {@code must not be empty} failures thrown out of Feign whenever several threads made their first
 * call to the same client at once — see {@link FeignConfig} for the mechanism.
 */
class FeignConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class))
            .withUserConfiguration(FeignConfig.class);

    @Test
    void convertersAreFullyBuiltBeforeAnyRequestThreadCanSeeThem() {
        // The race is only possible while the list is half-built. Proving it is complete at the end
        // of context refresh — before any executor exists — is what closes it.
        runner.run(context -> {
            FeignHttpMessageConverters converters = context.getBean(FeignHttpMessageConverters.class);
            assertThat(converters.getConverters())
                    .isNotEmpty()
                    .doesNotContainNull();
        });
    }

    @Test
    void feignClientContextsReuseTheEagerBeanInsteadOfBuildingTheirOwn() {
        // The whole fix rests on this: FeignClientsConfiguration declares its own
        // FeignHttpMessageConverters as @ConditionalOnMissingBean, so each per-client child context
        // must back off to ours. If it ever stopped doing so, every client would go back to
        // lazily building a list of its own and the race would return silently.
        runner.run(context -> {
            FeignHttpMessageConverters eager = context.getBean(FeignHttpMessageConverters.class);
            FeignClientFactory factory = context.getBean(FeignClientFactory.class);

            assertThat(factory.getInstance("rag-service", FeignHttpMessageConverters.class))
                    .isSameAs(eager);
            assertThat(factory.getInstance("ai-service", FeignHttpMessageConverters.class))
                    .isSameAs(eager);
        });
    }
}