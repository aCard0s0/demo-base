# One build for the whole reactor. Every service stage copies out of this one, so it runs once.
# JDK 26 to match java.version in pom.xml: Lombok 1.18.46 fails on JDK 27's javac (EndPosTable).
FROM eclipse-temurin:26-jdk-alpine AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY apps apps
COPY libs libs
# Cache mount rather than a copy-poms-first dance: one line, and it survives changes to the module list.
# The web module is left out: it has its own node stage below.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    -pl apps/auth-service,apps/todo-service,apps/agent-service -am

# Shared runtime: a JRE and an unprivileged user, so no service runs as root.
FROM eclipse-temurin:27-jre-alpine AS runtime
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
USER app

FROM runtime AS auth-service
COPY --from=build /src/apps/auth-service/target/auth-service-*.jar app.jar
EXPOSE 9081
CMD ["java", "-jar", "app.jar"]

FROM runtime AS todo-service
COPY --from=build /src/apps/todo-service/target/todo-service-*.jar app.jar
EXPOSE 9082
CMD ["java", "-jar", "app.jar"]

FROM runtime AS agent-service
COPY --from=build /src/apps/agent-service/target/agent-service-*.jar app.jar
EXPOSE 9083
CMD ["java", "-jar", "app.jar"]

FROM node:24-alpine AS web-build
WORKDIR /src
COPY apps/web/package.json apps/web/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY apps/web ./
RUN npm run build

# The Angular build on nginx, which also proxies /api and /mcp to the services (docker/nginx.conf).
FROM nginx:1.29-alpine AS web
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=web-build /src/dist/web/browser /usr/share/nginx/html
