FROM mockserver/mockserver:7.0.0

COPY build/libs/mockserver-openapi-scenario-extension-all.jar /libs/mockserver-openapi-scenario-extension.jar

ENV MOCKSERVER_INITIALIZATION_CLASS=com.bilal_fazlani.mockserver.openapi.scenario.OpenApiScenarioInitializer
ENV MOCKSERVER_OPENAPI_SCENARIOS_SPEC_DIR=/config/openapi
ENV MOCKSERVER_OPENAPI_SCENARIOS_DOCS_PATH=/openapi/docs
ENV MOCKSERVER_LOG_LEVEL=WARN

HEALTHCHECK NONE

ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-cp", "/mockserver-netty-jar-with-dependencies.jar:/libs/*", "-Dmockserver.propertyFile=/config/mockserver.properties", "com.bilal_fazlani.mockserver.openapi.scenario.OpenApiScenarioProxy"]
