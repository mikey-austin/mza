FROM eclipse-temurin:23-jre-alpine
RUN apk add alsa-utils
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8080/tcp
ENTRYPOINT ["java","-jar","/app.jar"]
