FROM amazoncorretto:17-alpine3.20 AS build
WORKDIR /app
RUN apk add --no-cache maven
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM amazoncorretto:17-alpine3.20
WORKDIR /app
COPY --from=build /app/target/client-1.0.0.jar client.jar
ENTRYPOINT ["java", "-jar", "client.jar"]
