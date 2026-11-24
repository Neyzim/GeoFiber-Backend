# Imagem do backend para hospedagem em container (Render). Passo a passo: docs/PUBLICAR-GRATUITO.md

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
# Dependências primeiro: a camada fica em cache enquanto o build.gradle não muda.
# "sh ./gradlew" porque o gradlew está sem permissão de execução no Git.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN sh ./gradlew dependencies --no-daemon > /dev/null
COPY src src
RUN sh ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
RUN useradd --system --no-create-home geofiber
WORKDIR /app
COPY --from=build /workspace/build/libs/geofiber-backend-0.0.1-SNAPSHOT.jar app.jar
USER geofiber
# O plano gratuito do Render tem 512 MB: heap até 60% (~300 MB), o resto fica para metaspace, threads
# e código da JVM (com 75% uma importação grande passaria dos 512 MB e o container seria morto).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
