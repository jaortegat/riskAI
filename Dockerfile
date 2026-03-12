# =============================================================================
# Stage 1: Build
# =============================================================================
FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /app

# Copy Maven wrapper and pom.xml first for layer caching
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code and build
COPY src/ src/

RUN ./mvnw package -B -DskipTests

# Extract Spring Boot jar with launcher for optimized startup
RUN java -Djarmode=tools -jar target/riskai-game-*.jar extract --launcher --destination extracted

# =============================================================================
# Stage 2: Runtime
# =============================================================================
FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

# Create a non-root user for security
RUN addgroup --system riskai && adduser --system --ingroup riskai riskai

# Copy extracted application
COPY --from=build --chown=riskai:riskai /app/extracted/ ./

USER riskai

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseZGC", \
  "org.springframework.boot.loader.launch.JarLauncher"]
