FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl registry-service -am clean package -DskipTests -B

FROM gcr.io/distroless/java17-debian12:nonroot
WORKDIR /app
COPY --from=build /workspace/registry-service/target/registry-service-*.jar /app/app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
