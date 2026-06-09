package com.bilal_fazlani.mockserver.openapi.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

import java.util.HashMap;
import org.junit.jupiter.api.Test;

class OpenApiScenarioProxyTest {

    @Test
    void treatsOnlyDocsPathAndChildrenAsDocsRoutes() {
        assertThat(OpenApiScenarioProxy.isDocsRoute("/openapi/docs", "/openapi/docs"))
                .isTrue();
        assertThat(OpenApiScenarioProxy.isDocsRoute("/openapi/docs/", "/openapi/docs"))
                .isTrue();
        assertThat(OpenApiScenarioProxy.isDocsRoute("/openapi/docs/scenarios.js", "/openapi/docs"))
                .isTrue();
        assertThat(OpenApiScenarioProxy.isDocsRoute("/openapi/docs-extra", "/openapi/docs"))
                .isFalse();
        assertThat(OpenApiScenarioProxy.isDocsRoute("/dashboard", "/openapi/docs"))
                .isFalse();
        assertThat(OpenApiScenarioProxy.isDocsRoute("/v1/fraud/assessment", "/openapi/docs")).isFalse();
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
    void treatsAdminIndexStylesheetsAsIndexAssets() {
        assertThat(OpenApiScenarioProxy.isIndexStylesRoute("/openapi/docs/styles.css", "/openapi/docs"))
                .isTrue();
        assertThat(OpenApiScenarioProxy.isIndexStylesRoute("/dashboard/styles.css", "/dashboard"))
                .isTrue();
        assertThat(OpenApiScenarioProxy.isIndexStylesRoute("/openapi/docs-extra/styles.css", "/openapi/docs"))
                .isFalse();
    }

    @Test
    void treatsRootIndexAndRootAssetsAsAdminRoutes() {
        assertThat(OpenApiScenarioProxy.isRootIndexRoute("/")).isTrue();
        assertThat(OpenApiScenarioProxy.isRootIndexRoute("/index.html")).isTrue();
        assertThat(OpenApiScenarioProxy.isRootAssetRoute("/styles.css")).isTrue();
        assertThat(OpenApiScenarioProxy.isRootAssetRoute("/icons/mockserver-icon.png")).isTrue();
        assertThat(OpenApiScenarioProxy.isRootAssetRoute("/icons/swagger-svgrepo-com.svg")).isTrue();
        assertThat(OpenApiScenarioProxy.isRootAssetRoute("/icons/home-icon.png")).isTrue();
        assertThat(OpenApiScenarioProxy.isRootAssetRoute("/v1/fraud/assessment")).isFalse();
    }

    @Test
    void mapsCleanDashboardRoutesToMockServerDashboardRoutes() {
        assertThat(OpenApiScenarioProxy.dashboardTargetOverride("/dashboard")).isEqualTo("/mockserver/dashboard");
        assertThat(OpenApiScenarioProxy.dashboardTargetOverride("/dashboard/")).isEqualTo("/mockserver/dashboard/");
        assertThat(OpenApiScenarioProxy.dashboardTargetOverride("/dashboard/mockserver/retrieve"))
                .isEqualTo("/mockserver/dashboard/mockserver/retrieve");
        assertThat(OpenApiScenarioProxy.dashboardTargetOverride("/mockserver/dashboard")).isNull();
        assertThat(OpenApiScenarioProxy.dashboardTargetOverride("/v1/fraud/assessment")).isNull();
    }

    @Test
    void configuresChildMockServerOnInternalPortWithoutDocsExpectations() {
        var environment = new HashMap<String, String>();
        environment.put("SERVER_PORT", "1080");
        environment.put(OpenApiScenarioProxy.DOCS_ENABLED_ENV, "true");

        OpenApiScenarioProxy.configureMockServerEnvironment(
                environment, 1081, java.nio.file.Path.of("/config/openapi/accertify.yaml"));

        assertThat(environment.get("SERVER_PORT")).isEqualTo("1081");
        assertThat(environment.get(OpenApiScenarioProxy.DOCS_ENABLED_ENV)).isEqualTo("false");
        assertThat(environment.get(OpenApiScenarioInitializer.SPEC_PATH_ENV))
                .isEqualTo("/config/openapi/accertify.yaml");
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
