FROM amazoncorretto:17-al2023-maven AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM amazoncorretto:17-al2023
WORKDIR /app
COPY --from=build /app/target/client-1.0.0.jar client.jar
ENTRYPOINT ["java", "-jar", "client.jar"]
