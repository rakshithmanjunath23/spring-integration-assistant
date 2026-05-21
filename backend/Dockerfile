# Build stage
FROM eclipse-temurin:11-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src

# Download dependencies first (layer caching)
RUN apt-get update && apt-get install -y maven && \
    mvn dependency:resolve

# Build application
RUN mvn clean package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:11-jre-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
