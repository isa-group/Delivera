# Contexto de build: ./engines, porque el motor depende de routing-core y hay que compilarlo antes.
FROM eclipse-temurin:22-jdk-alpine AS build
RUN apk add --no-cache maven
WORKDIR /app
# El agregador lista todos los modulos y Maven exige que existan aunque solo se construya uno:
# se copian todos los pom (solo los pom) y las fuentes unicamente de lo que se compila.
COPY pom.xml .
COPY routing-core/pom.xml routing-core/
COPY greedy-engine/pom.xml greedy-engine/
COPY random-engine/pom.xml random-engine/
COPY genetic-engine/pom.xml genetic-engine/
COPY annealing-engine/pom.xml annealing-engine/
RUN mvn -q -pl genetic-engine -am dependency:go-offline
COPY routing-core/src routing-core/src
COPY genetic-engine/src genetic-engine/src
RUN mvn -q -pl genetic-engine -am -DskipTests package

FROM eclipse-temurin:22-jre-alpine
WORKDIR /app
COPY --from=build /app/genetic-engine/target/genetic-engine-1.0.0-SNAPSHOT.jar app.jar
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
EXPOSE 8093
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8093/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
