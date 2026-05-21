FROM maven:3.8.6-eclipse-temurin-11 AS builder
WORKDIR /app

COPY backend/frameworks/zqzl-framework/pom.xml ./frameworks/zqzl-framework/
COPY backend/services/sso-server/pom.xml ./services/sso-server/

RUN mvn -f ./frameworks/zqzl-framework/pom.xml dependency:go-offline -B -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 2>/dev/null || true
RUN mvn -f ./services/sso-server/pom.xml dependency:go-offline -B -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true 2>/dev/null || true

COPY backend/frameworks/zqzl-framework ./frameworks/zqzl-framework
COPY backend/services/sso-server ./services/sso-server

RUN mvn -f ./frameworks/zqzl-framework/pom.xml clean install -DskipTests -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true
RUN mvn -f ./services/sso-server/pom.xml clean package -DskipTests -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true

FROM eclipse-temurin:11-jre
WORKDIR /app

COPY --from=builder /app/services/sso-server/target/sso-server-1.0.0.jar app.jar

EXPOSE 8080 9090

ENTRYPOINT ["java", "-jar", "app.jar"]
