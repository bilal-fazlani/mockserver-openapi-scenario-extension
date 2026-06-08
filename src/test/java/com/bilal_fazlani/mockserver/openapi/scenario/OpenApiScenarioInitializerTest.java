package com.bilal_fazlani.mockserver.openapi.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenApiScenarioInitializerTest {

    @Test
    void readsSpecPathFromSystemProperty() {
        String previousSpecPath = System.getProperty(OpenApiScenarioInitializer.SPEC_PATH_PROPERTY);
        String previousDocsEnabled = System.getProperty(OpenApiScenarioInitializer.DOCS_ENABLED_PROPERTY);
        try {
            System.setProperty(OpenApiScenarioInitializer.SPEC_PATH_PROPERTY, "src/test/resources/petstore-scenarios.yaml");
            System.clearProperty(OpenApiScenarioInitializer.DOCS_ENABLED_PROPERTY);

            var expectations = new OpenApiScenarioInitializer().initializeExpectations();

            assertThat(expectations).hasSize(2);
            assertThat(expectations[0].getId()).isEqualTo("getPet-not-found");
            assertThat(expectations[1].getId()).isEqualTo("getPet-success");
        } finally {
            restore(OpenApiScenarioInitializer.SPEC_PATH_PROPERTY, previousSpecPath);
            restore(OpenApiScenarioInitializer.DOCS_ENABLED_PROPERTY, previousDocsEnabled);
        }
    }

    @Test
    void addsDocsExpectationsWhenDocsAreEnabled() {
        String previousSpecPath = System.getProperty(OpenApiScenarioInitializer.SPEC_PATH_PROPERTY);
        String previousDocsEnabled = System.getProperty(OpenApiScenarioInitializer.DOCS_ENABLED_PROPERTY);
        String previousDocsPath = System.getProperty(OpenApiScenarioInitializer.DOCS_PATH_PROPERTY);
        try {
            System.setProperty(OpenApiScenarioInitializer.SPEC_PATH_PROPERTY, "src/test/resources/petstore-scenarios.yaml");
            System.setProperty(OpenApiScenarioInitializer.DOCS_ENABLED_PROPERTY, "true");
            System.setProperty(OpenApiScenarioInitializer.DOCS_PATH_PROPERTY, "/mockserver/openapi/docs");

            var expectations = new OpenApiScenarioInitializer().initializeExpectations();

            assertThat(expectations)
                    .extracting(expectation -> expectation.getId())
                    .containsExactly(
                            "openapi-scenarios-docs-index",
                            "openapi-scenarios-docs-openapi",
                            "openapi-scenarios-docs-scenarios",
                            "openapi-scenarios-docs-styles-css",
                            "openapi-scenarios-docs-swagger-ui-css",
                            "openapi-scenarios-docs-swagger-ui-bundle-js",
                            "openapi-scenarios-docs-swagger-ui-standalone-preset-js",
                            "getPet-not-found",
                            "getPet-success");
        } finally {
            restore(OpenApiScenarioInitializer.SPEC_PATH_PROPERTY, previousSpecPath);
            restore(OpenApiScenarioInitializer.DOCS_ENABLED_PROPERTY, previousDocsEnabled);
            restore(OpenApiScenarioInitializer.DOCS_PATH_PROPERTY, previousDocsPath);
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
