FROM eclipse-temurin:17-jre-jammy

ARG SERVICE
WORKDIR /app

RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --home-dir /app --no-create-home --shell /usr/sbin/nologin app \
    && chown app:app /app

ENV HOME=/app \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"

COPY --chown=10001:10001 ${SERVICE}/target/*.jar /app/app.jar

USER app:app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
