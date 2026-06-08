package com.bilal_fazlani.mockserver.openapi.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenApiScenarioInitializerTest {

    @Test
    void readsSpecPathFromSystemProperty() {
        String previous = System.getProperty(OpenApiScenarioInitializer.SPEC_PATH_PROPERTY);
        try {
            System.setProperty(OpenApiScenarioInitializer.SPEC_PATH_PROPERTY, "src/test/resources/petstore-scenarios.yaml");

            var expectations = new OpenApiScenarioInitializer().initializeExpectations();

            assertThat(expectations).hasSize(2);
            assertThat(expectations[0].getId()).isEqualTo("getPet-not-found");
            assertThat(expectations[1].getId()).isEqualTo("getPet-success");
        } finally {
            if (previous == null) {
                System.clearProperty(OpenApiScenarioInitializer.SPEC_PATH_PROPERTY);
            } else {
                System.setProperty(OpenApiScenarioInitializer.SPEC_PATH_PROPERTY, previous);
            }
        }
    }
}
