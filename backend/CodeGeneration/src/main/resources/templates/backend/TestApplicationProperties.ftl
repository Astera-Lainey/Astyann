# Test profile — layered on top of application.properties when tests run with
# @ActiveProfiles("test"). Swaps MySQL for in-memory H2 so the suite needs no external database.
spring.datasource.url=jdbc:h2:mem:${project.databaseName}_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.show-sql=false

# Deterministic secret for tests (long enough for HMAC-SHA256 key derivation).
jwt.secret=test-only-secret-value-that-is-long-enough-for-hs256-signing

logging.level.root=WARN
