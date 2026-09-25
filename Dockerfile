FROM ghcr.io/jqlang/jq:latest AS jq-stage

FROM eclipse-temurin:25-jdk AS build
COPY --from=jq-stage /jq /usr/bin/jq
# Test that jq works after copying
RUN jq --version

ENV HOME=/app
RUN mkdir -p $HOME
WORKDIR $HOME
COPY . $HOME

# If you have a Vaadin Pro key, pass it as a secret with id "proKey":
#
#   $ docker build --secret id=proKey,src=$HOME/.vaadin/proKey .
#
# If you have a Vaadin Offline key, pass it as a secret with id "offlineKey":
#
#   $ docker build --secret id=offlineKey,src=$HOME/.vaadin/offlineKey .

RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=proKey \
    --mount=type=secret,id=offlineKey \
    sh -c 'PRO_KEY=$(jq -r ".proKey // empty" /run/secrets/proKey 2>/dev/null || echo "") && \
    OFFLINE_KEY=$(cat /run/secrets/offlineKey 2>/dev/null || echo "") && \
    ./mvnw clean package -DskipTests -Dvaadin.proKey=${PRO_KEY} -Dvaadin.offlineKey=${OFFLINE_KEY}'

FROM eclipse-temurin:25-jre-alpine

# Run as a normal user, not as root. The id is a number because Kubernetes (runAsNonRoot) cannot verify a name.
RUN addgroup -S -g 10001 stubu && adduser -S -u 10001 -G stubu stubu
WORKDIR /app
COPY --from=build --chown=10001:10001 /app/target/*.jar app.jar
USER 10001:10001

# The production profile (structured logs); everything else is configured with environment variables,
# see DEVELOPMENT.md. The heap follows the memory limit of the container.
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

# Kubernetes uses its own probes (deploy/kubernetes); this one is for plain Docker.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -q -O /dev/null "http://localhost:${PORT:-8080}/actuator/health/liveness" || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
