
#############################################################################################
###              Stage where Docker is building spring boot app using maven               ###
#############################################################################################
FROM maven:3.8.6-eclipse-temurin-8-alpine as build

ARG PROXY_SET=false
ARG PROXY_HOST=
ARG PROXY_PORT=

ARG MVN_PROFILES
ARG DPS_SERVICE_NAME

ENV DPS_SERVICE_NAME=${DPS_SERVICE_NAME}
ENV MVN_PROFILES=${MVN_PROFILES}

COPY . .

RUN mvn -B clean package \
        -DproxySet=${PROXY_SET} \
        -DproxyHost=${PROXY_HOST} \
        -DproxyPort=${PROXY_PORT} \
        -P ${MVN_PROFILES}
#############################################################################################

#############################################################################################
### Stage where Docker is running a java process to run a service built in previous stage ###
#############################################################################################
FROM eclipse-temurin:8-jre-alpine

ARG MVN_PROFILES
ARG DPS_SERVICE_NAME

COPY --from=build ${DPS_SERVICE_NAME}/target/${DPS_SERVICE_NAME}-*.jar /app/service.jar


CMD ["java", "-jar", "/app/service.jar"]
#############################################################################################
