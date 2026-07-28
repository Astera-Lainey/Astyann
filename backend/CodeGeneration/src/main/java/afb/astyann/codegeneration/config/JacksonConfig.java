package afb.astyann.codegeneration.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Single, shared {@link ObjectMapper} for the service. Previously {@code CodeGenerationService}
 * and {@code LogicInjectionService} each instantiated their own {@code new ObjectMapper()},
 * which drifted (no {@code jsr310}, no unknown-property tolerance). Injecting this bean keeps
 * PCSF deserialization and AI-response parsing consistent.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
