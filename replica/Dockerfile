FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Cache dependencies separately from source
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar replica.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "replica.jar"]
