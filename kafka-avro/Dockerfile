FROM eclipse-temurin:17-jre-alpine

ENV APP_HOME="/opt/example"
ENV JAVA_OPTS="-XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
ENV JAVA_CLASSPATH="$APP_HOME/libs/*:$APP_HOME:app.jar"
ENV TINI_VERSION="v0.19.0"

USER root

RUN apk --no-cache add shadow curl

# add Tini
RUN curl -fsSL https://github.com/krallin/tini/releases/download/"$TINI_VERSION"/tini-static-amd64 -o /usr/bin/tini \
    && chmod +x /usr/bin/tini
ENTRYPOINT ["/usr/bin/tini", "-e", "143", "--"]

# add user with UID 1001 (group 0 to access mounted volumes and storage)
RUN useradd -r -m -u 1001 -g 0 fvaleri

RUN mkdir -p $APP_HOME
WORKDIR $APP_HOME

COPY ./target/*.jar ./app.jar
COPY ./target/libs/ ./libs/

USER 1001

# run your program under Tini
CMD ["sh", "-c", "java $JAVA_OPTS -cp $JAVA_CLASSPATH it.fvaleri.kafka.Main $@"]
