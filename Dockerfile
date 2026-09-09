# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# -s .mvn/settings.xml aplica el mirror que evita los repositorios lentos de Flyway.
RUN chmod +x mvnw && ./mvnw -s .mvn/settings.xml dependency:go-offline -B

COPY src ./src
RUN ./mvnw -s .mvn/settings.xml clean package -DskipTests -B

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S officeplatform && adduser -S officeplatform -G officeplatform \
    && mkdir -p /app/backups \
    && chown -R officeplatform:officeplatform /app

COPY --from=build --chown=officeplatform:officeplatform /app/target/office-platform-0.0.1-SNAPSHOT.jar app.jar

USER officeplatform:officeplatform

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
