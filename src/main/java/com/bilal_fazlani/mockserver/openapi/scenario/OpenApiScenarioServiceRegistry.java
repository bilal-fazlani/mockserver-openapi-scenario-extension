package com.bilal_fazlani.mockserver.openapi.scenario;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Discovers OpenAPI scenario specs from a directory and routes requests to the matching service.
 */
final class OpenApiScenarioServiceRegistry {

    private static final String INDEX_RESOURCE_ROOT = "mockserver-openapi-scenario-index/";
    private static final Set<String> RESERVED_SERVICE_IDS =
            Set.of("assets", "favicon.ico", "apple-touch-icon.png", "frame", "mockserver");
    private static final Pattern SERVICE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final int DEFAULT_FIRST_SERVICE_PORT = 1081;

    private final List<Service> services;
    private final Map<String, Service> servicesById;

    private OpenApiScenarioServiceRegistry(List<Service> services) {
        this.services = List.copyOf(services);
        var byId = new LinkedHashMap<String, Service>();
        services.forEach(service -> byId.put(service.id(), service));
        this.servicesById = Map.copyOf(byId);
    }

    static OpenApiScenarioServiceRegistry load(Path specDirectory) {
        return load(specDirectory, "", DEFAULT_FIRST_SERVICE_PORT);
    }

    static OpenApiScenarioServiceRegistry load(Path specDirectory, String servicePorts, int firstServicePort) {
        var specPaths = discoverSpecPaths(specDirectory);
        var portOverrides = servicePortOverrides(servicePorts);
        validatePortOverrides(specPaths, portOverrides);
        var assignedPorts = assignedPorts(specPaths, portOverrides, firstServicePort);
        var services = specPaths.stream()
                .map(path -> new Service(
                        serviceId(path), path, assignedPorts.get(serviceId(path)), new OpenApiScenarioRequestValidator(path)))
                .toList();
        validateServiceIds(services);
        return new OpenApiScenarioServiceRegistry(services);
    }

    List<Service> services() {
        return services;
    }

    Optional<Service> service(String serviceId) {
        return Optional.ofNullable(servicesById.get(serviceId));
    }

    Service firstService() {
        if (services.isEmpty()) {
            throw new OpenApiScenarioException("No OpenAPI scenario specs are loaded.");
        }
        return services.get(0);
    }

    RequestMatch match(OpenApiScenarioProxy.InitialRequest initialRequest, byte[] body) {
        for (var service : services) {
            var validation = service.validator().validate(initialRequest, body);
            if (validation.applicable()) {
                return RequestMatch.matched(service, validation);
            }
        }
        return RequestMatch.unmatched();
    }

    String docsIndexHtml(String hostHeader, String docsPath) {
        var normalizedDocsPath = normalizedPath(docsPath);
        return indexHtml(
                "OpenAPI Scenario Docs",
                normalizedDocsPath + "/styles.css",
                homeLink("/"),
                services.stream()
                        .map(service -> new IndexLink(
                                service.id(),
                                service.publicPort(),
                                serviceUrl(hostHeader, service.publicPort(), normalizedDocsPath)))
                        .toList());
    }

    List<OpenApiScenarioDocs.StaticContent> docsContent(Service service, String docsPath) {
        return new OpenApiScenarioDocs().content(service.specPath(), normalizedPath(docsPath));
    }

    String dashboardIndexHtml(String hostHeader, String dashboardPath) {
        var normalizedDashboardPath = normalizedPath(dashboardPath);
        return indexHtml(
                "MockServer Dashboards",
                normalizedDashboardPath + "/styles.css",
                homeLink("/"),
                services.stream()
                        .map(service -> new IndexLink(
                                service.id(),
                                service.publicPort(),
                                serviceUrl(hostHeader, service.publicPort(), normalizedDashboardPath)))
                        .toList());
    }

    String rootIndexHtml(String dashboardPath, String docsPath) {
        return rootHtml(
                "MockServer",
                "/styles.css",
                List.of(
                        new IconLink(
                                "Dashboard",
                                normalizedPath(dashboardPath),
                                "/icons/mockserver-icon.png"),
                        new IconLink(
                                "OpenAPI UI",
                                normalizedPath(docsPath),
                                "/icons/swagger-svgrepo-com.svg")));
    }

    String indexStylesCss() {
        return resource(INDEX_RESOURCE_ROOT + "styles.css");
    }

    Optional<IndexAsset> indexAsset(String requestPath) {
        return switch (requestPath) {
            case "/styles.css" -> Optional.of(new IndexAsset(
                    "text/css; charset=utf-8", resourceBytes(INDEX_RESOURCE_ROOT + "styles.css")));
            case "/icons/mockserver-icon.png" -> Optional.of(new IndexAsset(
                    "image/png", resourceBytes(INDEX_RESOURCE_ROOT + "icons/mockserver-icon.png")));
            case "/icons/swagger-svgrepo-com.svg" -> Optional.of(new IndexAsset(
                    "image/svg+xml; charset=utf-8",
                    resourceBytes(INDEX_RESOURCE_ROOT + "icons/swagger-svgrepo-com.svg")));
            case "/icons/home-icon.png" -> Optional.of(new IndexAsset(
                    "image/png", resourceBytes(INDEX_RESOURCE_ROOT + "icons/home-icon.png")));
            default -> Optional.empty();
        };
    }

    private static List<Path> discoverSpecPaths(Path specDirectory) {
        if (!Files.isDirectory(specDirectory)) {
            throw new OpenApiScenarioException("OpenAPI scenario spec directory does not exist: " + specDirectory);
        }

        try (var paths = Files.list(specDirectory)) {
            var specs = paths.filter(Files::isRegularFile)
                    .filter(OpenApiScenarioServiceRegistry::isYaml)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            if (specs.isEmpty()) {
                throw new OpenApiScenarioException("No OpenAPI .yaml or .yml specs found in " + specDirectory);
            }
            return specs;
        } catch (IOException e) {
            throw new OpenApiScenarioException("Unable to list OpenAPI scenario spec directory " + specDirectory, e);
        }
    }

    private static boolean isYaml(Path path) {
        var fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".yaml") || fileName.endsWith(".yml");
    }

    private static String serviceId(Path specPath) {
        var fileName = specPath.getFileName().toString();
        var extensionStart = fileName.lastIndexOf('.');
        var serviceId = extensionStart > 0 ? fileName.substring(0, extensionStart) : fileName;
        if (!SERVICE_ID.matcher(serviceId).matches()) {
            throw new OpenApiScenarioException("Invalid OpenAPI service id " + serviceId + " from " + specPath);
        }
        if (RESERVED_SERVICE_IDS.contains(serviceId.toLowerCase(Locale.ROOT))) {
            throw new OpenApiScenarioException("Reserved OpenAPI service id " + serviceId + " from " + specPath);
        }
        return serviceId;
    }

    private static Map<String, Integer> servicePortOverrides(String servicePorts) {
        if (servicePorts == null || servicePorts.isBlank()) {
            return Map.of();
        }

        var overrides = new LinkedHashMap<String, Integer>();
        for (var entry : servicePorts.split(",")) {
            if (entry.isBlank()) {
                continue;
            }

            var parts = entry.split("=", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new OpenApiScenarioException("Invalid service port mapping: " + entry);
            }

            var serviceId = parts[0].trim();
            try {
                var port = Integer.parseInt(parts[1].trim());
                if (port < 1 || port > 65535) {
                    throw new OpenApiScenarioException("Invalid port for service " + serviceId + ": " + port);
                }
                if (overrides.put(serviceId, port) != null) {
                    throw new OpenApiScenarioException("Duplicate service port mapping for " + serviceId);
                }
            } catch (NumberFormatException e) {
                throw new OpenApiScenarioException("Invalid port for service " + serviceId + ": " + parts[1], e);
            }
        }
        return Map.copyOf(overrides);
    }

    private static void validatePortOverrides(List<Path> specPaths, Map<String, Integer> portOverrides) {
        var serviceIds = specPaths.stream().map(OpenApiScenarioServiceRegistry::serviceId).collect(java.util.stream.Collectors.toSet());
        for (var serviceId : portOverrides.keySet()) {
            if (!serviceIds.contains(serviceId)) {
                throw new OpenApiScenarioException("Service port mapping references unknown service: " + serviceId);
            }
        }

        var ports = new java.util.HashSet<Integer>();
        for (var entry : portOverrides.entrySet()) {
            if (!ports.add(entry.getValue())) {
                throw new OpenApiScenarioException("Duplicate service port: " + entry.getValue());
            }
        }
    }

    private static Map<String, Integer> assignedPorts(
            List<Path> specPaths, Map<String, Integer> portOverrides, int firstServicePort) {
        var assignedPorts = new LinkedHashMap<String, Integer>();
        var usedPorts = new java.util.HashSet<>(portOverrides.values());
        var nextPort = firstServicePort;
        for (var specPath : specPaths) {
            var serviceId = serviceId(specPath);
            var port = portOverrides.get(serviceId);
            while (port == null && usedPorts.contains(nextPort)) {
                nextPort++;
            }
            if (port == null) {
                port = nextPort++;
            }
            if (port < 1 || port > 65535) {
                throw new OpenApiScenarioException("Invalid port for service " + serviceId + ": " + port);
            }
            assignedPorts.put(serviceId, port);
            usedPorts.add(port);
        }
        return Map.copyOf(assignedPorts);
    }

    private static void validateServiceIds(List<Service> services) {
        var serviceIds = new java.util.HashSet<String>();
        for (var service : services) {
            if (!serviceIds.add(service.id())) {
                throw new OpenApiScenarioException("Duplicate OpenAPI service id: " + service.id());
            }
        }
    }

    private static String rootHtml(String title, String stylesPath, List<IconLink> links) {
        var linkTemplate = resource(INDEX_RESOURCE_ROOT + "root-link.html");
        var renderedLinks = new StringBuilder();
        for (var link : links) {
            renderedLinks.append(template(
                    linkTemplate,
                    Map.of(
                            "{{href}}", htmlAttribute(link.href()),
                            "{{iconPath}}", htmlAttribute(link.iconPath()),
                            "{{label}}", html(link.label()))));
        }
        return template(
                resource(INDEX_RESOURCE_ROOT + "root.html"),
                Map.of(
                        "{{title}}", html(title),
                        "{{stylesPath}}", htmlAttribute(stylesPath),
                        "{{links}}", renderedLinks.toString()));
    }

    private static String indexHtml(String title, String stylesPath, String homeLink, List<IndexLink> links) {
        var linkTemplate = resource(INDEX_RESOURCE_ROOT + "link.html");
        var renderedLinks = new StringBuilder();
        for (var link : links) {
            renderedLinks.append(template(
                    linkTemplate,
                    Map.of(
                            "{{href}}", htmlAttribute(link.href()),
                            "{{label}}", html(link.label()),
                            "{{port}}", String.valueOf(link.port()))));
        }
        return template(
                resource(INDEX_RESOURCE_ROOT + "index.html"),
                Map.of(
                        "{{title}}", html(title),
                        "{{stylesPath}}", htmlAttribute(stylesPath),
                        "{{homeLink}}", homeLink,
                        "{{links}}", renderedLinks.toString()));
    }

    private static String homeLink(String href) {
        return template(
                resource(INDEX_RESOURCE_ROOT + "home-link.html"),
                Map.of(
                        "{{href}}", htmlAttribute(href),
                        "{{iconPath}}", htmlAttribute("/icons/home-icon.png")));
    }

    private static String template(String source, Map<String, String> replacements) {
        var result = source;
        for (var entry : replacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static String resource(String resourcePath) {
        return new String(resourceBytes(resourcePath), UTF_8);
    }

    private static byte[] resourceBytes(String resourcePath) {
        try (var stream = OpenApiScenarioServiceRegistry.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new OpenApiScenarioException("Missing OpenAPI scenario index resource: " + resourcePath);
            }
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new OpenApiScenarioException("Unable to read OpenAPI scenario index resource: " + resourcePath, e);
        }
    }

    private static String normalizedPath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        var normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String serviceUrl(String hostHeader, int port, String path) {
        return "http://" + hostName(hostHeader) + ":" + port + normalizedPath(path);
    }

    private static String hostName(String hostHeader) {
        if (hostHeader == null || hostHeader.isBlank()) {
            return "localhost";
        }

        var host = hostHeader.trim();
        if (host.startsWith("[")) {
            var end = host.indexOf(']');
            return end > 0 ? host.substring(0, end + 1) : host;
        }

        var colon = host.indexOf(':');
        return colon > 0 ? host.substring(0, colon) : host;
    }

    private static String html(String value) {
        return htmlAttribute(value).replace("'", "&#39;");
    }

    private static String htmlAttribute(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    record Service(String id, Path specPath, int publicPort, OpenApiScenarioRequestValidator validator) {}

    record IndexAsset(String contentType, byte[] body) {}

    record RequestMatch(Service service, OpenApiScenarioRequestValidator.ValidationResult validation) {

        static RequestMatch matched(Service service, OpenApiScenarioRequestValidator.ValidationResult validation) {
            return new RequestMatch(service, validation);
        }

        static RequestMatch unmatched() {
            return new RequestMatch(null, OpenApiScenarioRequestValidator.ValidationResult.notApplicable());
        }

        boolean isMatched() {
            return service != null;
        }
    }

    private record IndexLink(String label, int port, String href) {}

    private record IconLink(String label, String href, String iconPath) {}
}
