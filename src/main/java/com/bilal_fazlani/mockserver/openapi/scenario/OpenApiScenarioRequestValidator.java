package com.bilal_fazlani.mockserver.openapi.scenario;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.openapi.OpenAPIRequestValidator;

/**
 * Validates incoming proxied requests against the configured OpenAPI request body schemas.
 */
final class OpenApiScenarioRequestValidator {

    private static final String NO_OPERATION_FOUND_PREFIX = "no operation found matching ";

    private final String specPayload;
    private final MockServerLogger logger;

    OpenApiScenarioRequestValidator(Path specPath) {
        this.logger = new MockServerLogger(OpenApiScenarioRequestValidator.class);
        this.specPayload = readSpec(specPath);
    }

    ValidationResult validate(OpenApiScenarioProxy.InitialRequest initialRequest, byte[] body) {
        var request = HttpRequest.request()
                .withMethod(initialRequest.method())
                .withPath(initialRequest.path());
        initialRequest.headers()
                .forEach((name, values) -> values.forEach(value -> request.withHeader(name, value)));
        request.withBody(new String(body, UTF_8));

        var errors = OpenAPIRequestValidator.validate(specPayload, request, logger);
        if (errors.size() == 1 && errors.get(0).startsWith(NO_OPERATION_FOUND_PREFIX)) {
            return ValidationResult.notApplicable();
        }
        if (errors.isEmpty()) {
            return ValidationResult.valid();
        }
        return ValidationResult.invalid(errors);
    }

    private static String readSpec(Path specPath) {
        try {
            return Files.readString(specPath);
        } catch (IOException e) {
            throw new OpenApiScenarioException("Unable to read OpenAPI document " + specPath, e);
        }
    }

    record ValidationResult(boolean applicable, List<String> errors) {

        static ValidationResult valid() {
            return new ValidationResult(true, List.of());
        }

        static ValidationResult notApplicable() {
            return new ValidationResult(false, List.of());
        }

        static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(true, List.copyOf(errors));
        }

        boolean isValid() {
            return errors.isEmpty();
        }
    }
}
