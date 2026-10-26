# ---------- Stage 1: build the JAR with Maven (JDK 21) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Copy pom.xml first so Docker caches the dependency download between builds
COPY app/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY app/src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- Stage 2: small runtime image (JRE 21), non-root user ----------
FROM eclipse-temurin:21-jre
RUN groupadd --system clinic && useradd --system --gid clinic --no-create-home clinic
WORKDIR /app
COPY --from=build /build/target/smart-clinic.jar /app/smart-clinic.jar
USER clinic

# The port is configurable: docker run -e SERVER_PORT=9090 ...
ENV SERVER_PORT=8081
EXPOSE 8081

# No secrets are baked into the image. Pass DB_URL, DB_USERNAME, DB_PASSWORD, MONGODB_URI and
# JWT_SECRET as environment variables (docker-compose.yml does this for you).
ENTRYPOINT ["java", "-jar", "/app/smart-clinic.jar"]
