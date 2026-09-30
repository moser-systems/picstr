FROM docker.io/eclipse-temurin:25-jre-alpine
RUN apk add --no-cache graphicsmagick
COPY ./target/picstr-*.jar /opt/app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s \
  CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java","-jar","/opt/app/app.jar"]
