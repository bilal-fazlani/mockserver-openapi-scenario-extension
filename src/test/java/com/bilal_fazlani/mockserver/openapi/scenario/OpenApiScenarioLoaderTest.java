package com.bilal_fazlani.mockserver.openapi.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;

class OpenApiScenarioLoaderTest {

    @Test
    void createsExpectationsFromOpenApiScenarioExtensions() {
        var expectations = new OpenApiScenarioLoader()
                .load(Path.of("src/test/resources/petstore-scenarios.yaml"));

        assertThat(expectations).hasSize(2);

        Expectation notFound = expectations.get(0);
        assertThat(notFound.getId()).isEqualTo("getPet-not-found");
        assertThat(notFound.getPriority()).isEqualTo(20);
        assertThat(notFound.getHttpRequest()).isInstanceOf(HttpRequest.class);

        HttpRequest notFoundRequest = (HttpRequest) notFound.getHttpRequest();
        assertThat(notFoundRequest.getMethod().getValue()).isEqualTo("GET");
        assertThat(notFoundRequest.getPath().getValue()).isEqualTo("/pets/{petId}");
        assertThat(notFoundRequest.getFirstPathParameter("petId")).isEqualTo("404");
        assertThat(notFound.getHttpResponse().getStatusCode()).isEqualTo(404);
        assertThat(notFound.getHttpResponse().getFirstHeader("content-type")).isEqualTo("application/json");
        assertThat(notFound.getHttpResponse().getBodyAsString()).isEqualTo("{\"code\":\"PET_NOT_FOUND\",\"message\":\"Pet was not found\"}");

        Expectation success = expectations.get(1);
        assertThat(success.getId()).isEqualTo("getPet-success");
        assertThat(success.getPriority()).isEqualTo(10);

        HttpRequest successRequest = (HttpRequest) success.getHttpRequest();
        assertThat(successRequest.getMethod().getValue()).isEqualTo("GET");
        assertThat(successRequest.getPath().getValue()).isEqualTo("/pets/{petId}");
        assertThat(success.getHttpResponse().getStatusCode()).isEqualTo(200);
        assertThat(success.getHttpResponse().getBodyAsString()).isEqualTo("{\"id\":\"123\",\"name\":\"Luna\"}");
    }

    @Test
    void rejectsMatcherlessScenarioBeforeTheLastScenario() {
        assertThatThrownBy(() -> new OpenApiScenarioLoader()
                        .load(Path.of("src/test/resources/matcherless-first.yaml")))
                .isInstanceOf(OpenApiScenarioException.class)
                .hasMessageContaining("matcherless")
                .hasMessageContaining("getPet");
    }

    @Test
    void rejectsAmbiguousResponseExampleNames() {
        assertThatThrownBy(() -> new OpenApiScenarioLoader()
                        .load(Path.of("src/test/resources/ambiguous-response-example.yaml")))
                .isInstanceOf(OpenApiScenarioException.class)
                .hasMessageContaining("ambiguous")
                .hasMessageContaining("problem");
    }

    @Test
    void requiresOperationIdForScenarioIds() {
        assertThatThrownBy(() -> new OpenApiScenarioLoader()
                        .load(Path.of("src/test/resources/missing-operation-id.yaml")))
                .isInstanceOf(OpenApiScenarioException.class)
                .hasMessageContaining("operationId");
    }
}
