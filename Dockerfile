FROM maven:3-eclipse-temurin-11 
WORKDIR /tmp
COPY pom.xml .
COPY src ./src/

