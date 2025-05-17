# Use a lightweight JDK 21 base image (significantly smaller than Ubuntu)
FROM eclipse-temurin:21-jdk-alpine

# Set working directory
WORKDIR /datasuite/json

# Copy jar file from build directory into new created directory
COPY build/libs/SN_JDataSuite-1.0.jar .

EXPOSE 8081

CMD ["java", "-jar", "SN_JDataSuite-1.0.jar"]