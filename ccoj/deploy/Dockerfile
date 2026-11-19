FROM eclipse-temurin:21-jdk-jammy
ARG SERVICE
WORKDIR /app
COPY ${SERVICE}/target/${SERVICE}-0.0.1-SNAPSHOT.jar application.jar
USER 65534:65534
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
