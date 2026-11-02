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
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=35.0 -XX:InitialRAMPercentage=8.0 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=24m -XX:MaxDirectMemorySize=32m -Xss256k -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -Djava.security.egd=file:/dev/urandom"
EXPOSE 10000
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
