# Base
FROM eclipse-temurin:21-jdk-alpine as base
RUN apk update && apk add --no-cache binutils openssl bash dos2unix

# Build
FROM base as build
RUN $JAVA_HOME/bin/jlink \
         --verbose \
         --add-modules ALL-MODULE-PATH \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /optimized-jdk-21
WORKDIR /app
COPY . .
RUN ls -la ./gradlew
RUN chmod +x ./gradlew
RUN dos2unix ./gradlew
RUN /bin/bash ./gradlew build

# Run
FROM alpine:latest
WORKDIR /app
ENV JAVA_HOME=/opt/jdk/jdk-21
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=build /optimized-jdk-21 $JAVA_HOME
COPY --from=build /app/build/libs/app*.jar app.jar

CMD ["java","-jar", "app.jar"]