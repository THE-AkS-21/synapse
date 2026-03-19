# ----- Build Stage -----
FROM maven:3.9-eclipse-temurin-17 AS build

# Set the working directory
WORKDIR /app

# Copy the pom.xml and download dependencies (Layer Caching)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the rest of the source code and build the application
COPY src ./src
# SENIOR FIX: Changed 'install' to 'package' (faster, does not pollute local .m2 repo)
RUN mvn clean package -DskipTests

# ----- Final Image Stage -----
# Use a slim Java 17 runtime
FROM eclipse-temurin:17-jre-jammy

# Set the working directory
WORKDIR /app

# SECURITY FIX: Create a non-root user to run the application
# This prevents privilege escalation attacks if the container is compromised
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

# Copy the built .jar file from the 'build' stage
COPY --from=build /app/target/synapse-0.0.1-SNAPSHOT.jar app.jar

# Expose the port your application runs on
EXPOSE 8080

# The command to run your application (with container-aware memory tuning)
ENTRYPOINT ["java","-XX:+UseG1GC","-XX:MaxRAMPercentage=75.0","-jar","app.jar"]