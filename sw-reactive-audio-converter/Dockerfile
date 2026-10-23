FROM maven:3.9.7-amazoncorretto-17-al2023 AS build
WORKDIR /app
RUN --mount=type=bind,source=pom.xml,target=pom.xml,readonly \
    --mount=type=bind,source=src,target=src,readonly \
    --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline && \
    mvn clean package -Dmaven.test.skip=true

FROM amazoncorretto:17.0.16-alpine3.22
ENV TZ=America/Mexico_City
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
COPY --from=build /app/target/*.jar /workspace/app.jar
COPY --chmod=0755 ./run.sh /run.sh
ENTRYPOINT ["/run.sh"]