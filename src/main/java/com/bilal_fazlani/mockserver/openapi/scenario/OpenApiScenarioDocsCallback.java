package com.bilal_fazlani.mockserver.openapi.scenario;

import static org.mockserver.model.HttpResponse.response;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

/**
 * Serves bundled documentation assets without embedding large bodies in expectation logs.
 */
public class OpenApiScenarioDocsCallback implements ExpectationResponseCallback {

    private static final Map<String, StaticResponse> RESPONSES = new ConcurrentHashMap<>();

    /**
     * Registers a static response for a documentation path.
     *
     * @param path request path
     * @param contentType response content type
     * @param body response body
     */
    public static void register(String path, String contentType, String body) {
        RESPONSES.put(path, new StaticResponse(contentType, body));
    }

    @Override
    public HttpResponse handle(HttpRequest httpRequest) {
        var staticResponse = RESPONSES.get(httpRequest.getPath().getValue());
        if (staticResponse == null) {
            return response().withStatusCode(404);
        }
        return response()
                .withStatusCode(200)
                .withHeader("content-type", staticResponse.contentType())
                .withHeader("cache-control", "no-store")
                .withBody(staticResponse.body());
    }

    private record StaticResponse(String contentType, String body) {}
}
