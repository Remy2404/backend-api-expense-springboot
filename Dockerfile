# Build stage
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw package -DskipTests -T 1C

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod

# Health check for Render
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s \
CMD curl -f http://localhost:${PORT:-8080}/api/actuator/health || exit 1

# Start app
ENTRYPOINT ["sh", "-c", "java \
  -Dserver.port=${PORT:-8080} \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseSerialGC \
  -XX:+TieredCompilation \
  -XX:TieredStopAtLevel=1 \
  -jar app.jar"]