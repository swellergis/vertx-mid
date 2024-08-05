

# 1st Docker build stage: build the project with Maven
FROM maven:3.6.3-openjdk-17 as builder
WORKDIR /project
COPY . /project/
RUN mvn package -DskipTests -B

# 2nd Docker build stage: copy builder output and configure entry point
FROM openjdk:17-alpine
ENV APP_DIR /application
ENV APP_FILE container-uber-jar.jar

EXPOSE 8080

WORKDIR $APP_DIR
COPY --from=builder /project/target/*-fat.jar $APP_DIR/$APP_FILE

#COPY --from=builder /project/keystore.p12 $APP_DIR/keystore.p12

ENTRYPOINT ["sh", "-c"]
CMD ["exec java -jar $APP_FILE"]

