package ${project.packageName}.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing (populates {@code @CreatedDate} / {@code @LastModifiedDate}).
 *
 * <p>Kept out of the main application class so that web slice tests ({@code @WebMvcTest}), which
 * start without a JPA context, are not forced to process the auditing registrar.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
