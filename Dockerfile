FROM eclipse-temurin:17-jdk-noble AS builder

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw \
    && ./mvnw -B -ntp dependency:go-offline

COPY src src

RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-noble

WORKDIR /app

COPY --from=builder /workspace/target/order-service.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
