package com.bilal_fazlani.mockserver.openapi.scenario;

import java.nio.file.Path;
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
        var specPath = specPath();
        var expectations = new OpenApiScenarioLoader().load(Path.of(specPath));
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
}
