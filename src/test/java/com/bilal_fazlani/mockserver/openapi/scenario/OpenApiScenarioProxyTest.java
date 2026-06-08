package com.bilal_fazlani.mockserver.openapi.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

import java.util.HashMap;
import org.junit.jupiter.api.Test;

class OpenApiScenarioProxyTest {

    @Test
    void treatsOnlyDocsPathAndChildrenAsDocsRoutes() {
        assertThat(OpenApiScenarioProxy.isDocsRoute("/mockserver/openapi/docs", "/mockserver/openapi/docs"))
                .isTrue();
        assertThat(OpenApiScenarioProxy.isDocsRoute("/mockserver/openapi/docs/", "/mockserver/openapi/docs"))
                .isTrue();
        assertThat(OpenApiScenarioProxy.isDocsRoute("/mockserver/openapi/docs/scenarios.js", "/mockserver/openapi/docs"))
                .isTrue();
        assertThat(OpenApiScenarioProxy.isDocsRoute("/mockserver/openapi/docs-extra", "/mockserver/openapi/docs"))
                .isFalse();
        assertThat(OpenApiScenarioProxy.isDocsRoute("/mockserver/dashboard", "/mockserver/openapi/docs"))
                .isFalse();
        assertThat(OpenApiScenarioProxy.isDocsRoute("/v1/fraud/assessment", "/mockserver/openapi/docs"))
                .isFalse();
    }

    @Test
    void treatsMockServerControlAndDashboardPathsAsMockServerRoutes() {
        assertThat(OpenApiScenarioProxy.isMockServerRoute("/mockserver")).isTrue();
        assertThat(OpenApiScenarioProxy.isMockServerRoute("/mockserver/dashboard")).isTrue();
        assertThat(OpenApiScenarioProxy.isMockServerRoute("/mockserver/retrieve")).isTrue();
        assertThat(OpenApiScenarioProxy.isMockServerRoute("/_mockserver_ui_websocket")).isTrue();
        assertThat(OpenApiScenarioProxy.isMockServerRoute("/v1/fraud/assessment")).isFalse();
    }

    @Test
    void configuresChildMockServerOnInternalPortWithoutDocsExpectations() {
        var environment = new HashMap<String, String>();
        environment.put("SERVER_PORT", "1080");
        environment.put(OpenApiScenarioProxy.DOCS_ENABLED_ENV, "true");

        OpenApiScenarioProxy.configureMockServerEnvironment(environment, 1081);

        assertThat(environment.get("SERVER_PORT")).isEqualTo("1081");
        assertThat(environment.get(OpenApiScenarioProxy.DOCS_ENABLED_ENV)).isEqualTo("false");
    }

    @Test
    void forcesPlainMockServerRequestsToCloseAfterOneResponse() {
        var initialRequest = OpenApiScenarioProxy.InitialRequest.parse(bytes("""
                GET /mockserver/dashboard HTTP/1.1\r
                Host: localhost:1080\r
                Connection: keep-alive\r
                \r
                """));

        var forwardedRequest = text(OpenApiScenarioProxy.forwardedInitialBytes(initialRequest));

        assertThat(forwardedRequest).contains("Connection: close");
        assertThat(forwardedRequest).doesNotContain("keep-alive");
    }

    @Test
    void keepsWebSocketUpgradeRequestsByteForByte() {
        var websocketRequest = bytes("""
                GET /_mockserver_ui_websocket HTTP/1.1\r
                Host: localhost:1080\r
                Connection: Upgrade\r
                Upgrade: websocket\r
                Sec-WebSocket-Key: abc\r
                \r
                """);
        var initialRequest = OpenApiScenarioProxy.InitialRequest.parse(websocketRequest);

        assertThat(OpenApiScenarioProxy.forwardedInitialBytes(initialRequest)).isEqualTo(websocketRequest);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(ISO_8859_1);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, ISO_8859_1);
    }
}
