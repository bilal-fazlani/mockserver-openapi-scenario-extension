package com.bilal_fazlani.mockserver.openapi.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DockerImageConfigurationTest {

    @Test
    void usesCleanOpenApiDocsPathInPublishedImage() throws IOException {
        var dockerfile = Files.readString(Path.of("Dockerfile"));

        assertThat(dockerfile)
                .contains("ENV MOCKSERVER_OPENAPI_SCENARIOS_DOCS_PATH=/openapi/docs")
                .doesNotContain("ENV MOCKSERVER_OPENAPI_SCENARIOS_DOCS_PATH=/mockserver/openapi/docs");
    }
}
