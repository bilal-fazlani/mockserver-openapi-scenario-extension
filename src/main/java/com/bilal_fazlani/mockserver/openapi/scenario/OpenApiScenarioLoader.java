package com.bilal_fazlani.mockserver.openapi.scenario;

import static org.mockserver.model.HttpResponse.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.HttpRequestSerializer;

/**
 * Converts x-mockserver-scenarios OpenAPI extensions into MockServer expectations.
 */
public class OpenApiScenarioLoader {

    /**
     * OpenAPI vendor extension name consumed by this loader.
     */
    public static final String EXTENSION_NAME = "x-mockserver-scenarios";

    private static final Set<String> HTTP_METHODS =
            Set.of("get", "put", "post", "delete", "patch", "head", "options", "trace");
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final HttpRequestSerializer REQUEST_SERIALIZER =
            new HttpRequestSerializer(new MockServerLogger(OpenApiScenarioLoader.class));
    private static final Pattern PATH_PARAMETER = Pattern.compile("\\{([^}/]+)}");

    /**
     * Creates an OpenAPI scenario loader.
     */
    public OpenApiScenarioLoader() {}

    /**
     * Loads an OpenAPI document and converts all x-mockserver-scenarios entries into expectations.
     *
     * @param openApiPath OpenAPI YAML or JSON file
     * @return generated MockServer expectations in match priority order
     */
    public List<Expectation> load(Path openApiPath) {
        var openApi = readOpenApi(openApiPath);
        var expectations = new ArrayList<Expectation>();

        for (var pathEntry : map(openApi.get("paths"), "OpenAPI paths").entrySet()) {
            var path = pathEntry.getKey();
            var pathItem = map(pathEntry.getValue(), "path item " + path);

            for (var operationEntry : pathItem.entrySet()) {
                var method = operationEntry.getKey();
                if (!HTTP_METHODS.contains(method.toLowerCase(Locale.ROOT))) {
                    continue;
                }

                var operation = map(operationEntry.getValue(), method.toUpperCase(Locale.ROOT) + " " + path);
                var scenarios = scenarios(operation);
                if (scenarios.isEmpty()) {
                    continue;
                }

                var operationId = requiredString(operation, "operationId", method.toUpperCase(Locale.ROOT) + " " + path);
                validateScenarioOrder(operationId, scenarios);

                for (int index = 0; index < scenarios.size(); index++) {
                    var priority = (scenarios.size() - index) * 10;
                    var scenario = map(scenarios.get(index), EXTENSION_NAME + " scenario " + (index + 1));
                    expectations.add(toExpectation(path, method, operation, operationId, scenario, priority));
                }
            }
        }

        return expectations;
    }

    private static Map<String, Object> readOpenApi(Path openApiPath) {
        try {
            return YAML_MAPPER.readValue(openApiPath.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new OpenApiScenarioException("Unable to read OpenAPI document " + openApiPath, e);
        }
    }

    private static List<Object> scenarios(Map<String, Object> operation) {
        var scenarios = operation.get(EXTENSION_NAME);
        if (scenarios == null) {
            return List.of();
        }
        if (scenarios instanceof List<?> list) {
            return List.copyOf(list);
        }
        throw new OpenApiScenarioException(EXTENSION_NAME + " must be an array");
    }

    private static void validateScenarioOrder(String operationId, List<Object> scenarios) {
        for (int index = 0; index < scenarios.size() - 1; index++) {
            var scenario = map(scenarios.get(index), EXTENSION_NAME + " scenario " + (index + 1));
            if (!scenario.containsKey("matcher")) {
                throw new OpenApiScenarioException(
                        "Scenario " + (index + 1) + " for operationId " + operationId
                                + " is matcherless; matcherless scenarios must be last.");
            }
        }
    }

    private static Expectation toExpectation(
            String path,
            String method,
            Map<String, Object> operation,
            String operationId,
            Map<String, Object> scenario,
            int priority) {
        var responseName = requiredString(scenario, "response", operationId);
        var scenarioId = operationId + "-" + responseName;
        var responseExample = findResponseExample(operation, responseName, scenarioId);
        var request = request(path, method, scenarioId, matcher(scenario));
        var response = toHttpResponse(responseExample);

        return Expectation.when(request, priority)
                .withId(scenarioId)
                .thenRespond(response);
    }

    private static Map<String, Object> matcher(Map<String, Object> scenario) {
        var matcher = scenario.get("matcher");
        if (matcher == null) {
            return Map.of();
        }
        return map(matcher, "matcher");
    }

    private static HttpRequest request(
            String path, String method, String scenarioId, Map<String, Object> matcher) {
        var request = new LinkedHashMap<String, Object>();
        request.put("method", method.toUpperCase(Locale.ROOT));
        request.put("path", path);
        var wildcardPathParameters = wildcardPathParameters(path);
        if (!wildcardPathParameters.isEmpty()) {
            request.put("pathParameters", wildcardPathParameters);
        }

        for (var entry : matcher.entrySet()) {
            if (entry.getKey().equals("method") || entry.getKey().equals("path")) {
                throw new OpenApiScenarioException(
                        "Scenario " + scenarioId + " matcher cannot override " + entry.getKey() + ".");
            }
            if (entry.getKey().equals("pathParameters") && request.containsKey("pathParameters")) {
                request.put("pathParameters", mergedPathParameters(request.get("pathParameters"), entry.getValue()));
            } else {
                request.put(entry.getKey(), entry.getValue());
            }
        }

        try {
            return REQUEST_SERIALIZER.deserialize(JSON_MAPPER.writeValueAsString(request));
        } catch (IOException e) {
            throw new OpenApiScenarioException("Unable to serialize request matcher for scenario " + scenarioId, e);
        } catch (RuntimeException e) {
            throw new OpenApiScenarioException("Invalid request matcher for scenario " + scenarioId, e);
        }
    }

    private static Map<String, Object> wildcardPathParameters(String path) {
        var parameters = new LinkedHashMap<String, Object>();
        var matcher = PATH_PARAMETER.matcher(path);
        while (matcher.find()) {
            parameters.put(matcher.group(1), List.of(".*"));
        }
        return parameters;
    }

    private static Map<String, Object> mergedPathParameters(Object base, Object override) {
        var merged = new LinkedHashMap<>(map(base, "generated pathParameters"));
        merged.putAll(map(override, "matcher pathParameters"));
        return merged;
    }

    private static HttpResponse toHttpResponse(ResponseExample responseExample) {
        return response()
                .withStatusCode(responseExample.statusCode())
                .withHeader("content-type", responseExample.contentType())
                .withBody(responseBody(responseExample));
    }

    private static String responseBody(ResponseExample responseExample) {
        var value = responseExample.value();
        if (value instanceof String stringValue) {
            return stringValue;
        }
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new OpenApiScenarioException("Unable to serialize response example " + responseExample.name(), e);
        }
    }

    private static ResponseExample findResponseExample(
            Map<String, Object> operation, String exampleName, String scenarioId) {
        var matches = new ArrayList<ResponseExample>();
        var responses = map(operation.get("responses"), "responses for " + scenarioId);

        for (var responseEntry : responses.entrySet()) {
            var status = responseEntry.getKey();
            var response = map(responseEntry.getValue(), "response " + status + " for " + scenarioId);
            var content = map(response.get("content"), "response content " + status + " for " + scenarioId);

            for (var contentEntry : content.entrySet()) {
                var contentType = contentEntry.getKey();
                var media = map(contentEntry.getValue(), "media type " + contentType + " for " + scenarioId);
                var examples = map(media.get("examples"), "examples " + contentType + " for " + scenarioId);

                if (examples.containsKey(exampleName)) {
                    var example = map(examples.get(exampleName), "example " + exampleName + " for " + scenarioId);
                    if (!example.containsKey("value")) {
                        throw new OpenApiScenarioException(
                                "Scenario " + scenarioId + " references response example "
                                        + exampleName + " without a value.");
                    }
                    matches.add(new ResponseExample(
                            exampleName,
                            statusCode(status, scenarioId),
                            contentType,
                            example.get("value")));
                }
            }
        }

        if (matches.isEmpty()) {
            throw new OpenApiScenarioException(
                    "Scenario " + scenarioId + " references missing response example " + exampleName + ".");
        }
        if (matches.size() > 1) {
            throw new OpenApiScenarioException(
                    "Scenario " + scenarioId + " references ambiguous response example " + exampleName + ".");
        }
        return matches.get(0);
    }

    private static int statusCode(String status, String scenarioId) {
        try {
            return Integer.parseInt(status);
        } catch (NumberFormatException e) {
            throw new OpenApiScenarioException(
                    "Scenario " + scenarioId + " uses non-numeric response status " + status + ".", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value, String context) {
        if (value instanceof Map<?, ?> source) {
            var result = new LinkedHashMap<String, Object>();
            source.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        throw new OpenApiScenarioException("Expected object at " + context + ".");
    }

    private static String requiredString(Map<String, Object> source, String key, String context) {
        var value = source.get(key);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }
        throw new OpenApiScenarioException("Missing required string " + key + " in " + context + ".");
    }

    private record ResponseExample(String name, int statusCode, String contentType, Object value) {}
}
