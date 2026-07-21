FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src/ src/
RUN mvn -DskipTests package -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/fms-gateway-1.0.0-SNAPSHOT.jar app.jar
RUN mkdir -p instances-MD-CVRP && \
    addgroup -S spring && adduser -S spring -G spring && \
    chown -R spring:spring instances-MD-CVRP
USER spring:spring
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8090/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
