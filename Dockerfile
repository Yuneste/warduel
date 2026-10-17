# --- Build stage: compile and package the Spring Boot jar ---
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app

# Download dependencies first so they are cached when only the source changes
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src src
RUN mvn -B -q -DskipTests package && cp target/warduel-*.jar app.jar

# --- Runtime stage: just a JRE and the jar ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home mathwars
USER mathwars
COPY --from=build /app/app.jar app.jar

# Render's free plan has 512 MB of memory: keep the heap well inside it
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
# server.port reads $PORT, which Render sets
ENTRYPOINT ["java", "-jar", "app.jar"]
