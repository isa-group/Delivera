# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:resolve -q
COPY src/ src/
RUN mvn -DskipTests package -q

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/fms-routing-service-1.0.0-SNAPSHOT.jar app.jar
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8082
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
