package com.bilal_fazlani.mockserver.openapi.scenario;

import java.nio.file.Path;
import java.util.ArrayList;
import org.mockserver.mock.Expectation;
import org.mockserver.server.initialize.ExpectationInitializer;

/**
 * MockServer startup hook that loads expectations from an OpenAPI document with
 * x-mockserver-scenarios extensions.
 */
public class OpenApiScenarioInitializer implements ExpectationInitializer {

    /**
     * Java system property used to point at the OpenAPI scenario specification.
     */
    public static final String SPEC_PATH_PROPERTY = "mockserver.openapi.scenarios.spec";

    /**
     * Environment variable used to point at the OpenAPI scenario specification.
     */
    public static final String SPEC_PATH_ENV = "MOCKSERVER_OPENAPI_SCENARIOS_SPEC";

    /**
     * Java system property used to enable the OpenAPI documentation UI.
     */
    public static final String DOCS_ENABLED_PROPERTY = "mockserver.openapi.scenarios.docs.enabled";

    /**
     * Environment variable used to enable the OpenAPI documentation UI.
     */
    public static final String DOCS_ENABLED_ENV = "MOCKSERVER_OPENAPI_SCENARIOS_DOCS_ENABLED";

    /**
     * Java system property used to configure the OpenAPI documentation UI path.
     */
    public static final String DOCS_PATH_PROPERTY = "mockserver.openapi.scenarios.docs.path";

    /**
     * Environment variable used to configure the OpenAPI documentation UI path.
     */
    public static final String DOCS_PATH_ENV = "MOCKSERVER_OPENAPI_SCENARIOS_DOCS_PATH";

    /**
     * Creates a MockServer-compatible initializer.
     */
    public OpenApiScenarioInitializer() {}

    /**
     * Loads all expectations from the configured OpenAPI scenario specification.
     *
     * @return expectations for MockServer startup
     */
    @Override
    public Expectation[] initializeExpectations() {
        var specPath = Path.of(specPath());
        var expectations = new ArrayList<Expectation>();
        if (docsEnabled()) {
            expectations.addAll(new OpenApiScenarioDocs().load(specPath, docsPath()));
        }
        expectations.addAll(new OpenApiScenarioLoader().load(specPath));
        return expectations.toArray(Expectation[]::new);
    }

    private static String specPath() {
        var propertyValue = System.getProperty(SPEC_PATH_PROPERTY);
        if (hasText(propertyValue)) {
            return propertyValue;
        }

        var envValue = System.getenv(SPEC_PATH_ENV);
        if (hasText(envValue)) {
            return envValue;
        }

        throw new OpenApiScenarioException(
                "OpenAPI scenario spec path is required. Set system property "
                        + SPEC_PATH_PROPERTY
                        + " or environment variable "
                        + SPEC_PATH_ENV
                        + ".");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean docsEnabled() {
        return Boolean.parseBoolean(configuredValue(DOCS_ENABLED_PROPERTY, DOCS_ENABLED_ENV, "false"));
    }

    private static String docsPath() {
        return configuredValue(DOCS_PATH_PROPERTY, DOCS_PATH_ENV, OpenApiScenarioDocs.DEFAULT_DOCS_PATH);
    }

    private static String configuredValue(String property, String env, String defaultValue) {
        var propertyValue = System.getProperty(property);
        if (hasText(propertyValue)) {
            return propertyValue;
        }

        var envValue = System.getenv(env);
        if (hasText(envValue)) {
            return envValue;
        }

        return defaultValue;
    }
}
