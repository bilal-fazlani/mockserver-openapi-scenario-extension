package com.bilal_fazlani.mockserver.openapi.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;

class OpenApiScenarioDocsTest {

    private static final Path DOCS_SCENARIOS = Path.of("src/test/resources/docs-scenarios.yaml");

    @Test
    void createsDocsExpectationsForConfiguredPath() {
        var expectations = new OpenApiScenarioDocs().load(DOCS_SCENARIOS, "/mockserver/openapi/docs");

        assertThat(expectations)
                .extracting(Expectation::getId)
                .containsExactly(
                        "openapi-scenarios-docs-index",
                        "openapi-scenarios-docs-openapi",
                        "openapi-scenarios-docs-scenarios",
                        "openapi-scenarios-docs-styles-css",
                        "openapi-scenarios-docs-swagger-ui-css",
                        "openapi-scenarios-docs-swagger-ui-bundle-js",
                        "openapi-scenarios-docs-swagger-ui-standalone-preset-js");

        assertPath(expectations.get(0), "/mockserver/openapi/docs");
        assertPath(expectations.get(1), "/mockserver/openapi/docs/openapi.yaml");
        assertPath(expectations.get(2), "/mockserver/openapi/docs/scenarios.js");
        assertPath(expectations.get(3), "/mockserver/openapi/docs/styles.css");
        assertPath(expectations.get(4), "/mockserver/openapi/docs/swagger-ui.css");
        assertPath(expectations.get(5), "/mockserver/openapi/docs/swagger-ui-bundle.js");
        assertPath(expectations.get(6), "/mockserver/openapi/docs/swagger-ui-standalone-preset.js");
    }

    @Test
    void servesTheOriginalOpenApiDocument() {
        var expectations = new OpenApiScenarioDocs().load(DOCS_SCENARIOS, "/mockserver/openapi/docs");

        var response = expectations.get(1).getHttpResponse();

        assertThat(response.getFirstHeader("content-type")).isEqualTo("application/yaml; charset=utf-8");
        assertThat(response.getBodyAsString()).contains("x-mockserver-scenarios:");
        assertThat(response.getBodyAsString()).contains("operationId: assessFraud");
    }

    @Test
    void servesScenarioRendererScript() {
        var expectations = new OpenApiScenarioDocs().load(DOCS_SCENARIOS, "/mockserver/openapi/docs");

        var response = expectations.get(2).getHttpResponse();

        assertThat(response.getFirstHeader("content-type")).isEqualTo("application/javascript; charset=utf-8");
        assertThat(response.getBodyAsString()).contains("MockServerOpenApiScenarios");
        assertThat(response.getBodyAsString()).contains("MockServerOpenApiScenarioData");
        assertThat(response.getBodyAsString()).contains("POST /v1/fraud/assessment");
        assertThat(response.getBodyAsString()).contains("body JSONPath");
        assertThat(response.getBodyAsString()).contains("jsonPath");
        assertThat(response.getBodyAsString()).contains("$[?(@.email =~ /.*reject.*/i)]");
        assertThat(response.getBodyAsString()).contains("\"default\"");
        assertThat(response.getBodyAsString()).contains("\" -> \"");
        assertThat(response.getBodyAsString()).contains("\"when \" + summarizeMatcher");
        assertThat(response.getBodyAsString()).contains(".responses-wrapper");
        assertThat(response.getBodyAsString()).contains("No MockServer scenario mapping");
        assertThat(response.getBodyAsString()).doesNotContain("Raw matcher");
        assertThat(response.getBodyAsString()).doesNotContain("JSON.stringify(scenario.matcher");
        assertThat(response.getBodyAsString()).doesNotContain("MockServer scenarios");
        assertThat(response.getBodyAsString()).doesNotContain("<ol");
        assertThat(response.getBodyAsString()).doesNotContain("\"li\"");
    }

    @Test
    void servesIndexAndStylesFromTemplates() {
        var expectations = new OpenApiScenarioDocs().load(DOCS_SCENARIOS, "/mockserver/openapi/docs");

        var indexResponse = expectations.get(0).getHttpResponse();
        var stylesResponse = expectations.get(3).getHttpResponse();

        assertThat(indexResponse.getBodyAsString()).contains("/mockserver/openapi/docs/styles.css");
        assertThat(indexResponse.getBodyAsString()).contains("docExpansion: \"full\"");
        assertThat(indexResponse.getBodyAsString()).doesNotContain("<style>");

        assertThat(stylesResponse.getFirstHeader("content-type")).isEqualTo("text/css; charset=utf-8");
        assertThat(stylesResponse.getBodyAsString()).contains(".mockserver-scenarios-panel");
        assertThat(stylesResponse.getBodyAsString()).contains("white-space: nowrap");
    }

    @Test
    void servesBundledSwaggerUiAssetsThroughCallback() {
        var expectations = new OpenApiScenarioDocs().load(DOCS_SCENARIOS, "/mockserver/openapi/docs");

        assertThat(expectations.get(4).getHttpResponseClassCallback().getCallbackClass())
                .isEqualTo(OpenApiScenarioDocsCallback.class.getName());
        assertThat(expectations.get(5).getHttpResponseClassCallback().getCallbackClass())
                .isEqualTo(OpenApiScenarioDocsCallback.class.getName());

        var cssResponse = new OpenApiScenarioDocsCallback()
                .handle(HttpRequest.request().withPath("/mockserver/openapi/docs/swagger-ui.css"));
        var bundleResponse = new OpenApiScenarioDocsCallback()
                .handle(HttpRequest.request().withPath("/mockserver/openapi/docs/swagger-ui-bundle.js"));

        assertThat(cssResponse.getFirstHeader("content-type"))
                .isEqualTo("text/css; charset=utf-8");
        assertThat(cssResponse.getFirstHeader("cache-control")).isEqualTo("no-store");
        assertThat(cssResponse.getBodyAsString()).contains(".swagger-ui");

        assertThat(bundleResponse.getFirstHeader("content-type"))
                .isEqualTo("application/javascript; charset=utf-8");
        assertThat(bundleResponse.getBodyAsString()).contains("SwaggerUIBundle");
    }

    @Test
    void normalizesDocsPathWithTrailingSlash() {
        var expectations = new OpenApiScenarioDocs().load(DOCS_SCENARIOS, "/mockserver/openapi/docs/");

        assertPath(expectations.get(0), "/mockserver/openapi/docs");
        assertPath(expectations.get(1), "/mockserver/openapi/docs/openapi.yaml");
    }

    private static void assertPath(Expectation expectation, String path) {
        assertThat(expectation.getHttpRequest()).isInstanceOf(HttpRequest.class);
        var request = (HttpRequest) expectation.getHttpRequest();
        assertThat(request.getMethod().getValue()).isEqualTo("GET");
        assertThat(request.getPath().getValue()).isEqualTo(path);
    }
}
