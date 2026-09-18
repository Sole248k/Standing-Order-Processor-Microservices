FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG JAR_FILE=ewb-gateway/target/*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
