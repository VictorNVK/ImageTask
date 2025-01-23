# Stage 1: Build the project using Gradle and JDK 21
FROM gradle:8.11.1-jdk21 AS builder

# Set the working directory
WORKDIR /app

# Copy the Gradle build files and the source code into the container
COPY build.gradle /app/
COPY src /app/src

# Build the project
RUN gradle build -x test

# Stage 2: Run the application using JDK 21
FROM openjdk:21-jdk-slim

# Set the working directory
WORKDIR /app

# Install FFmpeg and FFprobe
RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*

# Copy the built artifact from the builder stage
COPY --from=builder /app/build/libs/*.jar /app/your-application.jar

# Run the application
ENTRYPOINT ["java", "-jar", "/app/your-application.jar"]