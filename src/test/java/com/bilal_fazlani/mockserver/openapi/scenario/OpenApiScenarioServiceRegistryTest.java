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

        var html = registry.docsIndexHtml("localhost:1080", "/mockserver/openapi/docs");

        assertThat(html)
                .contains("http://localhost:1081/mockserver/openapi/docs")
                .contains("http://localhost:1082/mockserver/openapi/docs")
                .doesNotContain("/mockserver/openapi/docs/accertify")
                .doesNotContain("/mockserver/openapi/docs/worldpay");
    }

    @Test
    void buildsCentralDashboardIndexWithLinksToServicePorts() {
        var registry = OpenApiScenarioServiceRegistry.load(Path.of("src/test/resources/multi-specs"));

        var html = registry.dashboardIndexHtml("localhost:1080", "/mockserver/dashboard");

        assertThat(html)
                .contains("http://localhost:1081/mockserver/dashboard")
                .contains("http://localhost:1082/mockserver/dashboard")
                .doesNotContain("/mockserver/dashboard/accertify")
                .doesNotContain("/mockserver/dashboard/worldpay");
    }

    @Test
    void buildsServiceDocsContentAtStandardDocsPath() {
        var registry = OpenApiScenarioServiceRegistry.load(Path.of("src/test/resources/multi-specs"));

        var docs = registry.docsContent(
                registry.service("accertify").orElseThrow(), "/mockserver/openapi/docs");

        assertThat(docs)
                .extracting(OpenApiScenarioDocs.StaticContent::path)
                .contains(
                        "/mockserver/openapi/docs",
                        "/mockserver/openapi/docs/openapi.yaml",
                        "/mockserver/openapi/docs/scenarios.js");
        assertThat(docs)
                .extracting(OpenApiScenarioDocs.StaticContent::path)
                .doesNotContain("/mockserver/openapi/docs/accertify");
    }

    private static OpenApiScenarioProxy.InitialRequest request(String text) {
        return OpenApiScenarioProxy.InitialRequest.parse(text.getBytes(ISO_8859_1));
    }

    private static byte[] bytes(String body) {
        return body.getBytes(UTF_8);
    }
}
