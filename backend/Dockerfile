# Build stage
FROM eclipse-temurin:11-jdk AS build
WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven wget && rm -rf /var/lib/apt/lists/*

# Copy Maven settings for better dependency resolution
COPY settings.xml /root/.m2/settings.xml

# Copy POM and source
COPY pom.xml .
COPY src ./src

# Download and cache dependencies first (layer caching)
RUN mvn dependency:resolve -q || true && \
    mvn dependency:resolve-plugins -q || true

# Build application
RUN mvn clean package -DskipTests -X

# Runtime stage
FROM eclipse-temurin:11-jre-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
