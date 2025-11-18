FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

COPY . .
RUN chmod +x ./gradlew

RUN ./gradlew :muffin-wallet-server:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /workspace/muffin-wallet-server/build/libs/*.jar /app/app.jar

ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","/app/app.jar"]
