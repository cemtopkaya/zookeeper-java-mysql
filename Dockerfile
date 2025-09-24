FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy the jar file
RUN cp target/db-reader-*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
