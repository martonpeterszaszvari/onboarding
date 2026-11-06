# Compile using Maven and JDK 21; keep Maven and sources out of the runtime image.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

# Run the executable Spring Boot jar as a non-root user.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S -G spring spring
WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
