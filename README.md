# mockserver-openapi-scenario-extension

A MockServer expectation initializer that turns OpenAPI `x-mockserver-scenarios` extensions into executable MockServer expectations at startup.

The extension lets an OpenAPI document act as both documentation and mock behavior. Response bodies, status codes, and content types stay in standard OpenAPI response examples. The custom extension only describes which example to use for a given request matcher.

## Requirements

- Java 17+
- MockServer 7.x
- OpenAPI 3.x YAML or JSON

## Extension Format

Each OpenAPI operation can define `x-mockserver-scenarios`:

```yaml
paths:
  /pets/{petId}:
    get:
      operationId: getPet
      responses:
        "200":
          description: Pet found
          content:
            application/json:
              examples:
                success:
                  value:
                    id: "123"
                    name: Luna
        "404":
          description: Pet not found
          content:
            application/json:
              examples:
                not-found:
                  value:
                    code: PET_NOT_FOUND
                    message: Pet was not found

      x-mockserver-scenarios:
        - matcher:
            pathParameters:
              petId:
                - "404"
          response: not-found

        - response: success
```

Each scenario has at most two fields:

- `matcher`: optional MockServer `httpRequest` matcher fields, except `method` and `path`.
- `response`: required OpenAPI response example name.

Generated fields:

- expectation id: `{operationId}-{response}`
- priority: derived from order, with earlier scenarios getting higher priority
- response status, content type, and body: derived from the referenced OpenAPI response example

Matcherless scenarios match only the OpenAPI method and path, so they must be last.

For OpenAPI templated paths such as `/pets/{petId}`, the extension adds wildcard path parameters to fallback scenarios so the OpenAPI path template still matches concrete requests.

## Build

```bash
./gradlew test
./gradlew runtimeJar
```

`runtimeJar` creates a Docker-friendly shaded jar:

```text
build/libs/mockserver-openapi-scenario-extension-<version>-all.jar
```

The shaded jar includes this extension's runtime dependencies but excludes MockServer itself, which is already present in the MockServer container.

## Docker Usage

Mount the shaded jar into MockServer's `/libs` directory and point the initializer at an OpenAPI file:

```yaml
services:
  mockserver:
    image: mockserver/mockserver:7.0.0
    ports:
      - "1080:1080"
    environment:
      MOCKSERVER_INITIALIZATION_CLASS: com.bilal_fazlani.mockserver.openapi.scenario.OpenApiScenarioInitializer
      MOCKSERVER_OPENAPI_SCENARIOS_SPEC: /config/openapi/api.yaml
    volumes:
      - ./api.yaml:/config/openapi/api.yaml:ro
      - ./build/libs/mockserver-openapi-scenario-extension-0.1.0-SNAPSHOT-all.jar:/libs/mockserver-openapi-scenario-extension.jar:ro
```

The spec path can also be provided as a Java system property:

```text
mockserver.openapi.scenarios.spec=/config/openapi/api.yaml
```

## Development

```bash
./gradlew test
```

The tests exercise scenario conversion without starting a MockServer process.

## Publishing

See [publishing.md](publishing.md).
