
# ---- Step 1: Build the application using Gradle and JDK 21 ----
FROM gradle:8.4-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle build --no-daemon

# ---- Step 2: Run the built JAR using OpenJDK 21 ----
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
