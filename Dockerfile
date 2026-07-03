# --- Stage 1: build the app (uses a heavy image with Gradle + JDK) ---
FROM gradle:8.10-jdk17 AS build
WORKDIR /app
# Copy the whole project in and produce the standalone distribution.
COPY . .
RUN gradle :server:installDist --no-daemon

# --- Stage 2: the actual box we ship (small, just a Java runtime) ---
FROM eclipse-temurin:17-jre
WORKDIR /opt/server
# Take ONLY the built server from stage 1 (not Gradle, not the source).
COPY --from=build /app/server/build/install/server/ ./
# The screen server listens on 8080.
EXPOSE 8080
ENTRYPOINT ["/opt/server/bin/server", "--serve", "8080"]
