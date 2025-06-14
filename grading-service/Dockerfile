FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /build

COPY . .

RUN mvn -q -pl grading-service -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /build/grading-service/target/*.jar app.jar

ENV PORT=10000
EXPOSE 10000

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Dserver.port=${PORT} -jar /app/app.jar"]
