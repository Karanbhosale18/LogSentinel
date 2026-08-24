# ── Stage 1: Build the React/Vite frontend ──────────────────────────
FROM node:18-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --production=false
COPY frontend/ ./
RUN npm run build

# ── Stage 2: Build the Spring Boot backend (with frontend embedded) ─
FROM maven:3.9.9-eclipse-temurin-17 AS backend-build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B
COPY backend/src ./src
# Embed the frontend build output into Spring Boot's static resources
COPY --from=frontend-build /frontend/dist/ ./src/main/resources/static/
RUN mvn clean package -DskipTests -B

# ── Stage 3: Slim runtime image ─────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=backend-build /app/target/*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
