# Dockerfile for deploying the Football Match Ticket Booking System
# to any platform that runs Docker images (Render, Railway, Fly.io, etc.)

FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copy all Java source files
COPY *.java ./

# Compile everything
RUN javac *.java

# Document the default port (cloud platforms override via $PORT)
EXPOSE 8080

# Start the server. Main reads $PORT automatically when set.
CMD ["java", "Main"]
