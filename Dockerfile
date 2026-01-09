FROM gradle:8.10.2-jdk17 AS builder
WORKDIR /app

COPY --chown=gradle:gradle . .
ENV GRADLE_OPTS="-Dorg.gradle.internal.http.connectionTimeout=60000 -Dorg.gradle.internal.http.socketTimeout=60000"
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/build/libs/bot-runner-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
