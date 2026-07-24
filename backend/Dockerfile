# --- Stage 1: build the jar with Maven ---
# Using a full Maven+JDK image here so the final image doesn't need Maven at all.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy just the pom first so Docker can cache the downloaded dependencies
# as a separate layer - they'll only be re-downloaded if pom.xml changes,
# not every time you edit a .java file.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# --- Stage 2: run it on a minimal JRE (no Maven, no JDK, much smaller image) ---
# Using the default (non-Alpine) Temurin JRE image here rather than the
# "-alpine" variant: the Alpine-based Temurin images are currently only
# published for linux/amd64. On Apple Silicon (arm64) or other non-amd64
# machines, Docker can't find a matching platform and the build fails with
# "no match for platform in manifest". This tag is published for both
# amd64 and arm64, at the cost of a somewhat larger image.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
