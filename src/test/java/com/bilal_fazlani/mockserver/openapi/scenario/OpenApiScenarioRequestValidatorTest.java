package com.bilal_fazlani.mockserver.openapi.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenApiScenarioRequestValidatorTest {

    private static final Path SPEC = Path.of("src/test/resources/docs-scenarios.yaml");

    private final OpenApiScenarioRequestValidator validator = new OpenApiScenarioRequestValidator(SPEC);

    @Test
    void validatesRequestBodiesForKnownOpenApiOperations() {
        var result = validator.validate(
                request("""
                        POST /v1/fraud/assessment HTTP/1.1\r
                        Host: localhost:1080\r
                        Content-Type: application/json\r
                        \r
                        """),
                bytes("{\"email\":\"jane.doe@example.com\"}"));

        assertThat(result.applicable()).isTrue();
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void rejectsRequestBodiesThatDoNotMatchKnownOpenApiOperationSchema() {
        var result = validator.validate(
                request("""
                        POST /v1/fraud/assessment HTTP/1.1\r
                        Host: localhost:1080\r
                        Content-Type: application/json\r
                        \r
                        """),
                bytes("{\"city\":\"London\"}"));

        assertThat(result.applicable()).isTrue();
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anySatisfy(error -> assertThat(error).contains("request body validation error"));
    }

    @Test
    void treatsUnknownOpenApiOperationsAsNotApplicable() {
        var result = validator.validate(
                request("""
                        POST /v1/unknown HTTP/1.1\r
                        Host: localhost:1080\r
                        Content-Type: application/json\r
                        \r
                        """),
                bytes("{\"email\":\"jane.doe@example.com\"}"));

        assertThat(result.applicable()).isFalse();
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void rejectsMissingRequiredRequestBodies() {
        var result = validator.validate(
                request("""
                        POST /v1/fraud/assessment HTTP/1.1\r
                        Host: localhost:1080\r
                        Content-Type: application/json\r
                        \r
                        """),
                new byte[0]);

        assertThat(result.applicable()).isTrue();
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("request body is required but was empty");
    }

    private static OpenApiScenarioProxy.InitialRequest request(String text) {
        return OpenApiScenarioProxy.InitialRequest.parse(text.getBytes(ISO_8859_1));
    }

    private static byte[] bytes(String body) {
        return body.getBytes(UTF_8);
    }
}
