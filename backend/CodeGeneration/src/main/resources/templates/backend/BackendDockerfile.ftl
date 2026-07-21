FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/${project.artifactId}-0.0.1-SNAPSHOT.jar app.jar
EXPOSE ${project.backendPort?c}
ENTRYPOINT ["java", "-jar", "app.jar"]
