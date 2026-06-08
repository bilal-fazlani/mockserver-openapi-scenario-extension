FROM mockserver/mockserver:7.0.0

COPY build/libs/mockserver-openapi-scenario-extension-all.jar /libs/mockserver-openapi-scenario-extension.jar

ENV MOCKSERVER_INITIALIZATION_CLASS=com.bilal_fazlani.mockserver.openapi.scenario.OpenApiScenarioInitializer
ENV MOCKSERVER_OPENAPI_SCENARIOS_DOCS_ENABLED=true
ENV MOCKSERVER_OPENAPI_SCENARIOS_DOCS_PATH=/mockserver/openapi/docs
ENV MOCKSERVER_LOG_LEVEL=WARN
