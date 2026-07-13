# Stage 1: Build the application using Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy the pom.xml and source code into the container
COPY pom.xml .
COPY src ./src

# Compile and package the absolute production jar file (skipping tests for faster deployment)
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime environment to keep the final image lightweight
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the built jar file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the internal port Spring Boot listens on
EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]