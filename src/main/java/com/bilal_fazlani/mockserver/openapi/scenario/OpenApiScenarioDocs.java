package com.bilal_fazlani.mockserver.openapi.scenario;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.mockserver.mock.Expectation;

/**
 * Creates MockServer expectations that serve OpenAPI scenario documentation.
 */
public class OpenApiScenarioDocs {

    /**
     * Default path where the documentation UI is served.
     */
    public static final String DEFAULT_DOCS_PATH = "/mockserver/openapi/docs";

    private static final int DOCS_PRIORITY = 1_000_000;
    private static final String DOCS_RESOURCE_ROOT = "mockserver-openapi-scenario-docs/";
    private static final String SWAGGER_UI_VERSION = "5.32.4";
    private static final Set<String> HTTP_METHODS =
            Set.of("get", "put", "post", "delete", "patch", "head", "options", "trace");
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    /**
     * Creates an OpenAPI scenario docs factory.
     */
    public OpenApiScenarioDocs() {}

    /**
     * Creates static documentation expectations for an OpenAPI document.
     *
     * @param openApiPath OpenAPI YAML or JSON file
     * @param docsPath HTTP path where the docs UI should be served
     * @return documentation expectations
     */
    public List<Expectation> load(Path openApiPath, String docsPath) {
        var normalizedDocsPath = normalizedDocsPath(docsPath);
        var openApi = readOpenApi(openApiPath);
        var scenarioData = scenarioData(openApi, openApiPath);

        return List.of(
                expectation(
                        "openapi-scenarios-docs-index",
                        normalizedDocsPath,
                        "text/html; charset=utf-8",
                        indexHtml(normalizedDocsPath)),
                expectation(
                        "openapi-scenarios-docs-openapi",
                        normalizedDocsPath + "/openapi.yaml",
                        "application/yaml; charset=utf-8",
                        openApi),
                expectation(
                        "openapi-scenarios-docs-scenarios",
                        normalizedDocsPath + "/scenarios.js",
                        "application/javascript; charset=utf-8",
                        scenarioRendererScript(scenarioData)),
                expectation(
                        "openapi-scenarios-docs-styles-css",
                        normalizedDocsPath + "/styles.css",
                        "text/css; charset=utf-8",
                        resource(DOCS_RESOURCE_ROOT + "styles.css")),
                swaggerUiAsset(
                        "openapi-scenarios-docs-swagger-ui-css",
                        normalizedDocsPath + "/swagger-ui.css",
                        "text/css; charset=utf-8",
                        "swagger-ui.css"),
                swaggerUiAsset(
                        "openapi-scenarios-docs-swagger-ui-bundle-js",
                        normalizedDocsPath + "/swagger-ui-bundle.js",
                        "application/javascript; charset=utf-8",
                        "swagger-ui-bundle.js"),
                swaggerUiAsset(
                        "openapi-scenarios-docs-swagger-ui-standalone-preset-js",
                        normalizedDocsPath + "/swagger-ui-standalone-preset.js",
                        "application/javascript; charset=utf-8",
                        "swagger-ui-standalone-preset.js"));
    }

    private static String normalizedDocsPath(String docsPath) {
        if (docsPath == null || docsPath.isBlank()) {
            return DEFAULT_DOCS_PATH;
        }

        var normalized = docsPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String readOpenApi(Path openApiPath) {
        try {
            return Files.readString(openApiPath, UTF_8);
        } catch (IOException e) {
            throw new OpenApiScenarioException("Unable to read OpenAPI document " + openApiPath, e);
        }
    }

    private static Expectation swaggerUiAsset(String id, String path, String contentType, String assetName) {
        var resourcePath = "META-INF/resources/webjars/swagger-ui-dist/"
                + SWAGGER_UI_VERSION
                + "/"
                + assetName;
        try (var stream = OpenApiScenarioDocs.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new OpenApiScenarioException("Missing bundled Swagger UI asset " + resourcePath + ".");
            }
            OpenApiScenarioDocsCallback.register(path, contentType, new String(stream.readAllBytes(), UTF_8));
            return callbackExpectation(id, path);
        } catch (IOException e) {
            throw new OpenApiScenarioException("Unable to read bundled Swagger UI asset " + resourcePath, e);
        }
    }

    private static Expectation expectation(String id, String path, String contentType, String body) {
        return Expectation.when(request().withMethod("GET").withPath(path), DOCS_PRIORITY)
                .withId(id)
                .thenRespond(response()
                        .withStatusCode(200)
                        .withHeader("content-type", contentType)
                        .withHeader("cache-control", "no-store")
                        .withBody(body));
    }

    private static Expectation callbackExpectation(String id, String path) {
        return Expectation.when(request().withMethod("GET").withPath(path), DOCS_PRIORITY)
                .withId(id)
                .thenRespond(callback(OpenApiScenarioDocsCallback.class));
    }

    private static String indexHtml(String docsPath) {
        return template(
                DOCS_RESOURCE_ROOT + "index.html",
                Map.of(
                        "{{docsPath}}", htmlAttribute(docsPath),
                        "{{openApiUrlJson}}", jsonString(docsPath + "/openapi.yaml")));
    }

    private static String scenarioData(String openApi, Path openApiPath) {
        try {
            var document = YAML_MAPPER.readValue(openApi, new TypeReference<Map<String, Object>>() {});
            var operations = new LinkedHashMap<String, Object>();

            for (var pathEntry : map(document.get("paths")).entrySet()) {
                var path = pathEntry.getKey();
                var pathItem = map(pathEntry.getValue());

                for (var operationEntry : pathItem.entrySet()) {
                    var method = operationEntry.getKey();
                    if (!HTTP_METHODS.contains(method.toLowerCase(Locale.ROOT))) {
                        continue;
                    }

                    var operation = map(operationEntry.getValue());
                    var scenarios = operation.get(OpenApiScenarioLoader.EXTENSION_NAME);
                    if (!(scenarios instanceof List<?> scenarioList) || scenarioList.isEmpty()) {
                        continue;
                    }

                    var operationData = new LinkedHashMap<String, Object>();
                    operationData.put("method", method.toUpperCase(Locale.ROOT));
                    operationData.put("path", path);
                    operationData.put("operationId", operation.get("operationId"));
                    operationData.put("scenarios", scenarioList);
                    operations.put(method.toUpperCase(Locale.ROOT) + " " + path, operationData);
                }
            }

            return JSON_MAPPER.writeValueAsString(operations);
        } catch (IOException e) {
            throw new OpenApiScenarioException("Unable to read OpenAPI document " + openApiPath, e);
        }
    }

    private static String scenarioRendererScript(String scenarioData) {
        return template(DOCS_RESOURCE_ROOT + "scenarios.js", Map.of("{{scenarioDataJson}}", scenarioData));
    }

    private static String template(String resourcePath, Map<String, String> replacements) {
        var template = resource(resourcePath);
        for (var entry : replacements.entrySet()) {
            template = template.replace(entry.getKey(), entry.getValue());
        }
        return template;
    }

    private static String resource(String resourcePath) {
        try (var stream = OpenApiScenarioDocs.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new OpenApiScenarioException("Missing bundled docs resource " + resourcePath + ".");
            }
            return new String(stream.readAllBytes(), UTF_8);
        } catch (IOException e) {
            throw new OpenApiScenarioException("Unable to read bundled docs resource " + resourcePath, e);
        }
    }

    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> source) {
            var result = new LinkedHashMap<String, Object>();
            source.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    private static String htmlAttribute(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String jsonString(String value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new OpenApiScenarioException("Unable to render docs URL " + value, e);
        }
    }
}
