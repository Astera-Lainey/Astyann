server.port=${project.backendPort?c}

# Datasource
spring.datasource.url=jdbc:mysql://<#noparse>${MYSQL_HOST:localhost}</#noparse>:<#noparse>${MYSQL_PORT:3306}</#noparse>/${project.databaseName}?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=<#noparse>${MYSQL_USER:</#noparse>${project.databaseUser}<#noparse>}</#noparse>
spring.datasource.password=<#noparse>${MYSQL_PASSWORD:}</#noparse>
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.open-in-view=false

# JWT
jwt.secret=<#noparse>${JWT_SECRET:change-me-please-use-a-long-random-secret-value}</#noparse>
jwt.access-token-validity-ms=${project.jwtAccessTokenValidityMs?c}
jwt.refresh-token-validity-ms=${project.jwtRefreshTokenValidityMs?c}

# CORS
app.cors.allowed-origins=${project.corsAllowedOriginsDev}

# Logging
logging.level.root=INFO
logging.level.${project.packageName}=DEBUG
