FROM amazoncorretto:17-al2023-maven AS build
WORKDIR /app
# Cache dependencies separately from source
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM amazoncorretto:17-al2023
WORKDIR /app
COPY --from=build /app/target/*.jar replica.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "replica.jar"]
