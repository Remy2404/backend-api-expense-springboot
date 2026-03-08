# Build stage
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy maven executable to the image
COPY mvnw .
COPY .mvn .mvn

# Copy the pom.xml file
COPY pom.xml .

# Resolve maven dependencies
# This step gives us a cached layer with all dependencies downloaded
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

# Copy the project source
COPY src src

# Package the application
RUN ./mvnw package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the jar from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port 8080
EXPOSE 8080

# Provide environment variables fallback defaults (can be overridden at runtime)
ENV SPRING_PROFILES_ACTIVE=prod

# Set the entrypoint
ENTRYPOINT ["java", "-jar", "app.jar"]
