FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
# Yavaş ağ: HTTP isteği başına tekrar (Maven 3.9 native transport) + adım başına 3 deneme.
# Read timeout istek düzeyinde tekrar edilmez; adım düzeyindeki döngü bunu karşılar.
# go-offline her şeyi çekmez (ör. zonky embedded-postgres binary'leri), package da indirir.
ENV MAVEN_OPTS="-Daether.connector.http.retryHandler.count=5 -Daether.connector.connectTimeout=60000 -Daether.connector.requestTimeout=120000"
RUN for i in 1 2 3; do mvn -q -B dependency:go-offline && exit 0; echo "go-offline denemesi $i başarısız, tekrar"; sleep 10; done; exit 1
COPY src ./src
RUN for i in 1 2 3; do mvn -q -B -DskipTests package && exit 0; echo "package denemesi $i başarısız, tekrar"; sleep 10; done; exit 1

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /build/target/fcareer-backend-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
