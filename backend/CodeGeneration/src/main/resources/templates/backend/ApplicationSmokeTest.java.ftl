package ${project.packageName};

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the full Spring context against an in-memory H2 database.
 *
 * <p>This is the highest-value generated test: it fails on the whole class of defects that
 * compile cleanly but break at startup — a missing bean, an unsatisfied constructor injection,
 * an invalid JPA mapping, a malformed {@code application.properties}, or a bad entity relationship.
 */
@SpringBootTest
@ActiveProfiles("test")
class ${appClassName}SmokeTest {

    @Test
    void contextLoads() {
        // Fails if the application context cannot be created.
    }
}
