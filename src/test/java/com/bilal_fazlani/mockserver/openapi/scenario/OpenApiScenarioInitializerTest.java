package com.bilal_fazlani.mockserver.openapi.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenApiScenarioInitializerTest {

    @Test
    void readsSpecPathFromSystemProperty() {
        String previousSpecPath = System.getProperty(OpenApiScenarioInitializer.SPEC_PATH_PROPERTY);
        try {
            System.setProperty(OpenApiScenarioInitializer.SPEC_PATH_PROPERTY, "src/test/resources/petstore-scenarios.yaml");

            var expectations = new OpenApiScenarioInitializer().initializeExpectations();

            assertThat(expectations).hasSize(2);
            assertThat(expectations[0].getId()).isEqualTo("getPet-not-found");
            assertThat(expectations[1].getId()).isEqualTo("getPet-success");
        } finally {
            restore(OpenApiScenarioInitializer.SPEC_PATH_PROPERTY, previousSpecPath);
        }
    }

    @Test
    void ignoresDocsExpectationProperties() {
        String previousSpecPath = System.getProperty(OpenApiScenarioInitializer.SPEC_PATH_PROPERTY);
        String previousDocsEnabled = System.getProperty(OpenApiScenarioProxy.DOCS_ENABLED_PROPERTY);
        String previousDocsPath = System.getProperty(OpenApiScenarioProxy.DOCS_PATH_PROPERTY);
        try {
            System.setProperty(OpenApiScenarioInitializer.SPEC_PATH_PROPERTY, "src/test/resources/petstore-scenarios.yaml");
            System.setProperty(OpenApiScenarioProxy.DOCS_ENABLED_PROPERTY, "true");
            System.setProperty(OpenApiScenarioProxy.DOCS_PATH_PROPERTY, "/mockserver/openapi/docs");

            var expectations = new OpenApiScenarioInitializer().initializeExpectations();

            assertThat(expectations)
                    .extracting(expectation -> expectation.getId())
                    .containsExactly("getPet-not-found", "getPet-success");
        } finally {
            restore(OpenApiScenarioInitializer.SPEC_PATH_PROPERTY, previousSpecPath);
            restore(OpenApiScenarioProxy.DOCS_ENABLED_PROPERTY, previousDocsEnabled);
            restore(OpenApiScenarioProxy.DOCS_PATH_PROPERTY, previousDocsPath);
        }
    }

    private static void restore(String property, String previous) {
        if (previous == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, previous);
        }
    }
}
