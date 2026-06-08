package com.bilal_fazlani.mockserver.openapi.scenario;

/**
 * Raised when an OpenAPI document cannot be converted into MockServer expectations.
 */
public class OpenApiScenarioException extends RuntimeException {

    /**
     * Creates an exception with a validation or parsing message.
     *
     * @param message details about the invalid OpenAPI scenario configuration
     */
    public OpenApiScenarioException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a validation or parsing message and root cause.
     *
     * @param message details about the invalid OpenAPI scenario configuration
     * @param cause original failure
     */
    public OpenApiScenarioException(String message, Throwable cause) {
        super(message, cause);
    }
}
