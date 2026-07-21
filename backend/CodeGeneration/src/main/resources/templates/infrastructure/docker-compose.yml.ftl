version: "3.9"

services:
  ${infra.databaseServiceName}:
    image: mysql:8
    container_name: ${infra.databaseServiceName}
    restart: unless-stopped
    environment:
      MYSQL_DATABASE: ${r"${DB_NAME:-"}${infra.databaseName}${r"}"}
      MYSQL_USER: ${r"${DB_USER:-"}${infra.databaseUser}${r"}"}
      MYSQL_PASSWORD: ${r"${DB_PASSWORD}"}
      MYSQL_ROOT_PASSWORD: ${r"${DB_ROOT_PASSWORD}"}
    volumes:
      - db-data:/var/lib/mysql
      - ./db/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -uroot -p$$MYSQL_ROOT_PASSWORD"]
      interval: 10s
      timeout: 5s
      retries: 10
    networks:
      - ${infra.artifactId}-net

  ${infra.backendServiceName}:
    build:
      context: ../backend
      dockerfile: Dockerfile
    container_name: ${infra.backendServiceName}
    depends_on:
      ${infra.databaseServiceName}:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://${infra.databaseServiceName}:3306/${infra.databaseName}?useSSL=false&allowPublicKeyRetrieval=true
      SPRING_DATASOURCE_USERNAME: ${r"${DB_USER:-"}${infra.databaseUser}${r"}"}
      SPRING_DATASOURCE_PASSWORD: ${r"${DB_PASSWORD}"}
      JWT_SECRET: ${r"${JWT_SECRET}"}
    ports:
      - "${infra.backendPort?c}:${infra.backendPort?c}"
    networks:
      - ${infra.artifactId}-net

  ${infra.frontendServiceName}:
    build:
      context: ../frontend
      dockerfile: Dockerfile
    container_name: ${infra.frontendServiceName}
    depends_on:
      - ${infra.backendServiceName}
    ports:
      - "${infra.frontendPort?c}:80"
    networks:
      - ${infra.artifactId}-net

volumes:
  db-data:

networks:
  ${infra.artifactId}-net:
    driver: bridge
