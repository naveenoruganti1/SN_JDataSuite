
# Operating System
FROM ubuntu:latest

# Update Oeration System
RUN apt update -y

# Install JDK-21
RUN apt install -y openjdk-21-jdk

# Create a Directory
RUN mkdir -p datasuite/json

WORKDIR /datasuite/json

# Copy jar file from build directory into new created directory
COPY build/libs/SN_JDataSuite-1.0.jar .

EXPOSE 8081

CMD ["java", "-jar", "SN_JDataSuite-1.0.jar"]