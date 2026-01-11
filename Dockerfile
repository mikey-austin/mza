FROM eclipse-temurin:25-jre-alpine
RUN apk add alsa-utils
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8080/tcp

# Set the desired GID for the audio group as a build argument
# Default to the same as debian to match our other containers
ARG AUDIO_GID=29

# Update the audio group GID if it exists, or create it if not
RUN apk add --no-cache shadow && \
    if getent group audio; then \
    delgroup audio; \
    fi && \
    addgroup -g $AUDIO_GID audio

ENTRYPOINT ["java","-jar","/app.jar"]
