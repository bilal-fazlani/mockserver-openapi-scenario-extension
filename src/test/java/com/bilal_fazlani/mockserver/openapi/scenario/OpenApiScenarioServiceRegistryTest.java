package com.bilal_fazlani.mockserver.openapi.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenApiScenarioServiceRegistryTest {

    @Test
    void loadsYamlAndYmlSpecsFromDirectoryAsNamedServices() {
        var registry = OpenApiScenarioServiceRegistry.load(Path.of("src/test/resources/multi-specs"));

        assertThat(registry.services())
                .extracting(OpenApiScenarioServiceRegistry.Service::id)
                .containsExactly("accertify", "worldpay");
        assertThat(registry.services())
                .extracting(service -> service.specPath().getFileName().toString())
                .containsExactly("accertify.yaml", "worldpay.yml");
        assertThat(registry.services())
                .extracting(OpenApiScenarioServiceRegistry.Service::publicPort)
                .containsExactly(1081, 1082);
    }

    @Test
    void appliesExplicitServicePortsByServiceId() {
        var registry = OpenApiScenarioServiceRegistry.load(
                Path.of("src/test/resources/multi-specs"), "worldpay=1182,accertify=1181", 1081);

        assertThat(registry.service("accertify")).get().extracting(OpenApiScenarioServiceRegistry.Service::publicPort)
                .isEqualTo(1181);
        assertThat(registry.service("worldpay")).get().extracting(OpenApiScenarioServiceRegistry.Service::publicPort)
                .isEqualTo(1182);
    }

    @Test
    void routesValidationToTheServiceWhoseOpenApiOperationMatchesTheRequest() {
        var registry = OpenApiScenarioServiceRegistry.load(Path.of("src/test/resources/multi-specs"));

        var match = registry.match(
                request("""
                        POST /worldpay/payments HTTP/1.1\r
                        Host: localhost:1080\r
                        Content-Type: application/json\r
                        \r
                        """),
                bytes("{\"amount\":100}"));

        assertThat(match.isMatched()).isTrue();
        assertThat(match.service().id()).isEqualTo("worldpay");
        assertThat(match.validation().isValid()).isTrue();
    }

    @Test
    void returnsValidationErrorsFromTheMatchedService() {
        var registry = OpenApiScenarioServiceRegistry.load(Path.of("src/test/resources/multi-specs"));

        var match = registry.match(
                request("""
                        POST /accertify/assessment HTTP/1.1\r
                        Host: localhost:1080\r
                        Content-Type: application/json\r
                        \r
                        """),
                bytes("{\"city\":\"London\"}"));

        assertThat(match.isMatched()).isTrue();
        assertThat(match.service().id()).isEqualTo("accertify");
        assertThat(match.validation().isValid()).isFalse();
        assertThat(match.validation().errors())
                .anySatisfy(error -> assertThat(error).contains("request body validation error"));
    }

    @Test
    void allowsSameApiPathAcrossDifferentServicePorts() {
        var registry = OpenApiScenarioServiceRegistry.load(Path.of("src/test/resources/collision-specs"));

        assertThat(registry.services())
                .extracting(OpenApiScenarioServiceRegistry.Service::id)
                .containsExactly("alpha", "beta");
        assertThat(registry.services())
                .extracting(OpenApiScenarioServiceRegistry.Service::publicPort)
                .containsExactly(1081, 1082);
    }

    @Test
    void buildsCentralDocsIndexWithLinksToServicePorts() {
        var registry = OpenApiScenarioServiceRegistry.load(Path.of("src/test/resources/multi-specs"));

        var html = registry.docsIndexHtml("localhost:1080", "/openapi/docs");

        assertThat(html)
                .contains("http://localhost:1081/openapi/docs")
                .contains("http://localhost:1082/openapi/docs")
                .contains("port 1081")
                .contains("port 1082")
                .contains("/openapi/docs/styles.css")
                .contains("href=\"/\"")
                .contains("Home")
                .doesNotContain("<style>")
                .doesNotContain("/openapi/docs/accertify")
                .doesNotContain("/openapi/docs/worldpay");
    }

    @Test
    void buildsCentralDashboardIndexWithLinksToServicePorts() {
        var registry = OpenApiScenarioServiceRegistry.load(Path.of("src/test/resources/multi-specs"));

        var html = registry.dashboardIndexHtml("localhost:1080", "/dashboard");

        assertThat(html)
                .contains("http://localhost:1081/dashboard")
                .contains("http://localhost:1082/dashboard")
                .contains("port 1081")
                .contains("port 1082")
                .contains("/dashboard/styles.css")
                .contains("href=\"/\"")
                .contains("Home")
                .doesNotContain("<style>")
                .doesNotContain("/dashboard/accertify")
                .doesNotContain("/dashboard/worldpay");
    }

    @Test
    void buildsRootIndexWithDashboardAndDocsLinks() {
        var registry = OpenApiScenarioServiceRegistry.load(Path.of("src/test/resources/multi-specs"));

        var html = registry.rootIndexHtml("/dashboard", "/openapi/docs");

        assertThat(html)
                .contains("Dashboard")
                .contains("OpenAPI UI")
                .contains("href=\"/dashboard\"")
                .contains("href=\"/openapi/docs\"")
                .contains("mockserver-icon.png")
                .contains("swagger-svgrepo-com.svg")
                .contains("/styles.css")
                .doesNotContain("port 1081")
                .doesNotContain("accertify");
    }

    @Test
    void buildsServiceDocsContentAtStandardDocsPath() {
        var registry = OpenApiScenarioServiceRegistry.load(Path.of("src/test/resources/multi-specs"));

        var docs = registry.docsContent(registry.service("accertify").orElseThrow(), "/openapi/docs");

        assertThat(docs)
                .extracting(OpenApiScenarioDocs.StaticContent::path)
                .contains(
                        "/openapi/docs",
                        "/openapi/docs/openapi.yaml",
                        "/openapi/docs/scenarios.js");
        assertThat(docs)
                .extracting(OpenApiScenarioDocs.StaticContent::path)
                .doesNotContain("/openapi/docs/accertify");
    }

    private static OpenApiScenarioProxy.InitialRequest request(String text) {
        return OpenApiScenarioProxy.InitialRequest.parse(text.getBytes(ISO_8859_1));
    }

    private static byte[] bytes(String body) {
        return body.getBytes(UTF_8);
    }
}
