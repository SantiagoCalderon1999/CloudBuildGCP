FROM --platform=linux/amd64 adoptopenjdk/openjdk11:jre-11.0.6_10-alpine
VOLUME /tmp
ARG JAR_FILE=target/*.jar
ARG DEPENDENCY=target/dependency
COPY ${JAR_FILE} app.jar
COPY ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY ${DEPENDENCY}/META-INF /app/META-INF
COPY ${DEPENDENCY}/BOOT-INF/classes /app
ENTRYPOINT ["java","-cp","app:app/lib/*","com.performancebot.bot.PerformanceCycleBotApplication"]