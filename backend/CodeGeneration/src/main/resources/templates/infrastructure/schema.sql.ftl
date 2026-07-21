-- Bootstrap schema for ${infra.appName}
-- Hibernate will handle table DDL via spring.jpa.hibernate.ddl-auto=update at first boot.

CREATE DATABASE IF NOT EXISTS ${infra.databaseName}
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS '${infra.databaseUser}'@'%' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON ${infra.databaseName}.* TO '${infra.databaseUser}'@'%';
FLUSH PRIVILEGES;
