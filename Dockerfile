FROM eclipse-temurin:25-jre-ubi10-minimal@sha256:15f9ffb1538ebc2e0eacd8444d744174e12b2dd03eb05e459bb31e528d6e814c

WORKDIR /app

COPY --chown=10001:10001 build/libs/*.jar app.jar

USER 10001:10001

EXPOSE 3457

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
