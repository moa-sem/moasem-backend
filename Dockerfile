# Build stage
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

# Copy gradle wrapper and build files first to cache dependencies
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts ./

# Ensure gradlew has execution permission and build dependencies to cache them
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copy source code and build bootJar
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# curl: 컨테이너 헬스 체크(/actuator/health)용
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as non-root user for security (Ubuntu/Debian syntax)
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# Copy built jar from the builder stage
COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

# Configure JVM options for container awareness
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
