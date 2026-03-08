FROM amazoncorretto:17-alpine3.20 AS build
WORKDIR /app
RUN apk add --no-cache maven
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM amazoncorretto:17-alpine3.20
WORKDIR /app
COPY --from=build /app/target/*.jar replica.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "replica.jar"]
