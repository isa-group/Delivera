FROM maven:3.9-eclipse-temurin-22-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src/ src/
RUN mvn -DskipTests package -q

FROM eclipse-temurin:22-jre-alpine
WORKDIR /app
COPY --from=build /app/target/genetic-engine-1.0.0-SNAPSHOT.jar app.jar
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
EXPOSE 8093
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8093/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
