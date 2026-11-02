# Build the executable Spring Boot jar with Java 21.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

# Small runtime image; memory is capped for Render's 512 MB instance size.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --uid 10001 --create-home appuser
COPY --from=build --chown=appuser:appuser /workspace/target/api-monitor-0.0.1-SNAPSHOT.jar /app/app.jar
USER appuser
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=40.0 -XX:InitialRAMPercentage=12.0 -XX:MaxMetaspaceSize=112m -XX:ReservedCodeCacheSize=32m -Xss512k -XX:+UseSerialGC -Djava.security.egd=file:/dev/urandom"
EXPOSE 10000
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
