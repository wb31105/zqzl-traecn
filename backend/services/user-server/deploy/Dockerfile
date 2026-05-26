ARG BUILDER_IMAGE=maven:3.9.6-eclipse-temurin-17
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre

FROM ${BUILDER_IMAGE} AS builder
WORKDIR /app

COPY backend/frameworks/zqzl-framework/pom.xml ./frameworks/zqzl-framework/
COPY backend/services/user-server/pom.xml ./services/user-server/

RUN mvn -f ./frameworks/zqzl-framework/pom.xml dependency:go-offline -B -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 2>/dev/null || true
RUN mvn -f ./services/user-server/pom.xml dependency:go-offline -B -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true 2>/dev/null || true

COPY backend/frameworks/zqzl-framework ./frameworks/zqzl-framework
COPY backend/services/user-server ./services/user-server

RUN mvn -f ./frameworks/zqzl-framework/pom.xml clean install -DskipTests -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true
RUN mvn -f ./services/user-server/pom.xml clean package -DskipTests -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true

FROM ${RUNTIME_IMAGE}
WORKDIR /app

COPY --from=builder /app/services/user-server/target/user-server-1.0.0.jar app.jar
COPY backend/services/user-server/deploy/start.sh /usr/local/bin/start.sh

RUN chmod +x /usr/local/bin/start.sh

EXPOSE 8081 9091

ENTRYPOINT ["start.sh"]
CMD ["docker"]
