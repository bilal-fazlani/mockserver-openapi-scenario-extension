package com.bilal_fazlani.mockserver.openapi.scenario;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Docker entry point that serves documentation outside MockServer and proxies API traffic to
 * MockServer.
 */
public final class OpenApiScenarioProxy {

    public static final String DOCS_ENABLED_PROPERTY = "mockserver.openapi.scenarios.docs.enabled";
    public static final String DOCS_ENABLED_ENV = "MOCKSERVER_OPENAPI_SCENARIOS_DOCS_ENABLED";
    public static final String DOCS_PATH_PROPERTY = "mockserver.openapi.scenarios.docs.path";
    public static final String DOCS_PATH_ENV = "MOCKSERVER_OPENAPI_SCENARIOS_DOCS_PATH";

    private static final String SERVER_PORT_ENV = "SERVER_PORT";
    private static final String INTERNAL_MOCKSERVER_PORT_ENV = "MOCKSERVER_OPENAPI_SCENARIOS_MOCKSERVER_PORT";
    private static final int DEFAULT_PUBLIC_PORT = 1080;
    private static final int DEFAULT_INTERNAL_MOCKSERVER_PORT = 1081;
    private static final int MAX_INITIAL_REQUEST_BYTES = 64 * 1024;
    private static final int MAX_VALIDATED_BODY_BYTES = 10 * 1024 * 1024;
    private static final Duration MOCKSERVER_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration MOCKSERVER_FORCE_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
    private static final Set<String> CONNECTION_HEADERS = Set.of("connection", "keep-alive", "proxy-connection");

    private OpenApiScenarioProxy() {}

    public static void main(String[] args) throws IOException {
        var specPath = Path.of(configuredValue(
                OpenApiScenarioInitializer.SPEC_PATH_PROPERTY,
                OpenApiScenarioInitializer.SPEC_PATH_ENV,
                null));
        var docsPath = normalizedDocsPath(configuredValue(
                DOCS_PATH_PROPERTY,
                DOCS_PATH_ENV,
                OpenApiScenarioDocs.DEFAULT_DOCS_PATH));
        var publicPort = configuredInt(SERVER_PORT_ENV, DEFAULT_PUBLIC_PORT);
        var mockServerPort = configuredInt(INTERNAL_MOCKSERVER_PORT_ENV, DEFAULT_INTERNAL_MOCKSERVER_PORT);

        var docs = docsByPath(new OpenApiScenarioDocs().content(specPath, docsPath));
        var requestValidator = new OpenApiScenarioRequestValidator(specPath);
        var serverSocket = serverSocket(publicPort);
        var mockServer = startMockServer(mockServerPort);
        var executor = Executors.newCachedThreadPool();
        var activeSockets = ConcurrentHashMap.<Socket>newKeySet();
        var shutdown = new Shutdown(serverSocket, executor, activeSockets, mockServer);
        Runtime.getRuntime().addShutdownHook(new Thread(shutdown, "openapi-scenario-proxy-shutdown"));

        int exitCode = 0;
        try {
            System.out.printf(
                    "Serving OpenAPI scenario docs at %s and proxying MockServer on port %d.%n",
                    docsPath, publicPort);
            executor.execute(() -> acceptLoop(
                    serverSocket,
                    executor,
                    activeSockets,
                    docs,
                    docsPath,
                    requestValidator,
                    mockServerPort,
                    shutdown));
            exitCode = mockServer.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exitCode = 130;
        } finally {
            shutdown.run();
        }

        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static boolean isDocsRoute(String requestPath, String docsPath) {
        var normalizedDocsPath = normalizedDocsPath(docsPath);
        return requestPath.equals(normalizedDocsPath)
                || requestPath.startsWith(normalizedDocsPath + "/");
    }

    static boolean isMockServerRoute(String requestPath) {
        return requestPath.equals("/mockserver")
                || requestPath.startsWith("/mockserver/")
                || requestPath.startsWith("/_mockserver");
    }

    static void configureMockServerEnvironment(Map<String, String> environment, int mockServerPort) {
        environment.put(SERVER_PORT_ENV, String.valueOf(mockServerPort));
        environment.put(DOCS_ENABLED_ENV, "false");
    }

    static byte[] forwardedInitialBytes(InitialRequest initialRequest) {
        if (initialRequest.isWebSocketUpgrade()) {
            return initialRequest.bytes();
        }

        var lines = initialRequest.headerText().split("\\r?\\n");
        var request = new StringBuilder();
        request.append(lines[0]).append("\r\n");
        Arrays.stream(lines)
                .skip(1)
                .filter(line -> !line.isBlank())
                .filter(line -> !isConnectionHeader(line))
                .forEach(line -> request.append(line).append("\r\n"));
        request.append("Connection: close\r\n\r\n");
        return request.toString().getBytes(ISO_8859_1);
    }

    private static ServerSocket serverSocket(int publicPort) throws IOException {
        var serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(publicPort));
        return serverSocket;
    }

    private static Process startMockServer(int mockServerPort) throws IOException {
        var command = new java.util.ArrayList<String>();
        command.add(javaExecutable());
        command.add("-Dfile.encoding=UTF-8");
        systemProperties().forEach((name, value) -> command.add("-D" + name + "=" + value));
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("org.mockserver.cli.Main");

        var processBuilder = new ProcessBuilder(command);
        configureMockServerEnvironment(processBuilder.environment(), mockServerPort);
        processBuilder.inheritIO();
        return processBuilder.start();
    }

    private static void acceptLoop(
            ServerSocket serverSocket,
            ExecutorService executor,
            Set<Socket> activeSockets,
            Map<String, OpenApiScenarioDocs.StaticContent> docs,
            String docsPath,
            OpenApiScenarioRequestValidator requestValidator,
            int mockServerPort,
            Shutdown shutdown) {
        while (!shutdown.isStopped()) {
            try {
                var clientSocket = serverSocket.accept();
                executor.execute(() -> handleClient(
                        clientSocket,
                        activeSockets,
                        docs,
                        docsPath,
                        requestValidator,
                        mockServerPort,
                        executor));
            } catch (SocketException e) {
                if (!shutdown.isStopped()) {
                    e.printStackTrace(System.err);
                    shutdown.run();
                }
                return;
            } catch (IOException e) {
                e.printStackTrace(System.err);
                shutdown.run();
                return;
            }
        }
    }

    private static void handleClient(
            Socket clientSocket,
            Set<Socket> activeSockets,
            Map<String, OpenApiScenarioDocs.StaticContent> docs,
            String docsPath,
            OpenApiScenarioRequestValidator requestValidator,
            int mockServerPort,
            ExecutorService executor) {
        activeSockets.add(clientSocket);
        try (clientSocket) {
            clientSocket.setTcpNoDelay(true);
            var clientInput = clientSocket.getInputStream();
            var initialRequest = readInitialRequest(clientInput);
            if (initialRequest == null) {
                return;
            }

            if (isDocsRoute(initialRequest.path(), docsPath)) {
                serveDocs(clientSocket.getOutputStream(), initialRequest, docs, docsPath);
                return;
            }

            if (isMockServerRoute(initialRequest.path()) || initialRequest.isWebSocketUpgrade()) {
                tunnelToMockServer(clientSocket, clientInput, initialRequest, new byte[0], mockServerPort, executor);
                return;
            }

            var body = readValidatedBody(clientInput, initialRequest);
            var validationResult = requestValidator.validate(initialRequest, body);
            if (validationResult.applicable() && !validationResult.isValid()) {
                sendValidationError(clientSocket.getOutputStream(), initialRequest.method(), validationResult.errors());
                return;
            }

            tunnelToMockServer(clientSocket, clientInput, initialRequest, body, mockServerPort, executor);
        } catch (OpenApiScenarioException e) {
            try {
                sendResponse(
                        clientSocket.getOutputStream(),
                        "GET",
                        400,
                        "Bad Request",
                        "application/json; charset=utf-8",
                        validationErrorBody(List.of(e.getMessage())));
            } catch (IOException ignored) {
            }
        } catch (IOException e) {
            if (!isExpectedSocketClose(e)) {
                e.printStackTrace(System.err);
            }
        } finally {
            activeSockets.remove(clientSocket);
        }
    }

    private static InitialRequest readInitialRequest(InputStream input) throws IOException {
        var buffer = new ByteArrayOutputStream();
        var previous4 = -1;
        var previous3 = -1;
        var previous2 = -1;
        var previous1 = -1;

        while (buffer.size() < MAX_INITIAL_REQUEST_BYTES) {
            var next = input.read();
            if (next == -1) {
                return buffer.size() == 0 ? null : InitialRequest.parse(buffer.toByteArray());
            }

            buffer.write(next);
            previous4 = previous3;
            previous3 = previous2;
            previous2 = previous1;
            previous1 = next;

            if ((previous4 == '\r' && previous3 == '\n' && previous2 == '\r' && previous1 == '\n')
                    || (previous2 == '\n' && previous1 == '\n')) {
                return InitialRequest.parse(buffer.toByteArray());
            }
        }

        throw new OpenApiScenarioException(
                "HTTP request headers exceeded " + MAX_INITIAL_REQUEST_BYTES + " bytes.");
    }

    private static byte[] readValidatedBody(InputStream input, InitialRequest initialRequest) throws IOException {
        var contentLength = initialRequest.contentLength();
        if (contentLength == 0) {
            return new byte[0];
        }
        if (contentLength > MAX_VALIDATED_BODY_BYTES) {
            throw new OpenApiScenarioException(
                    "HTTP request body exceeded " + MAX_VALIDATED_BODY_BYTES + " bytes.");
        }

        var body = input.readNBytes((int) contentLength);
        if (body.length != contentLength) {
            throw new OpenApiScenarioException(
                    "HTTP request body ended before Content-Length bytes were received.");
        }
        return body;
    }

    private static void serveDocs(
            OutputStream output,
            InitialRequest initialRequest,
            Map<String, OpenApiScenarioDocs.StaticContent> docs,
            String docsPath)
            throws IOException {
        var requestPath = initialRequest.path();
        if (requestPath.equals(docsPath + "/")) {
            requestPath = docsPath;
        }

        var content = docs.get(requestPath);
        if (content == null) {
            sendResponse(output, initialRequest.method(), 404, "Not Found", "text/plain; charset=utf-8", "Not found.");
            return;
        }

        sendResponse(output, initialRequest.method(), 200, "OK", content.contentType(), content.body());
    }

    private static void sendValidationError(OutputStream output, String method, List<String> errors)
            throws IOException {
        sendResponse(
                output,
                method,
                400,
                "Bad Request",
                "application/json; charset=utf-8",
                validationErrorBody(errors));
    }

    private static String validationErrorBody(List<String> errors) {
        var messages = errors.stream()
                .map(OpenApiScenarioProxy::jsonString)
                .collect(Collectors.joining(","));
        return "{\"error\":\"OpenAPI request validation failed\",\"messages\":["
                + messages
                + "]}";
    }

    private static String jsonString(String value) {
        return "\""
                + value.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                + "\"";
    }

    private static void sendResponse(
            OutputStream output, String method, int status, String reason, String contentType, String body)
            throws IOException {
        var bodyBytes = body.getBytes(UTF_8);
        var headers = "HTTP/1.1 "
                + status
                + " "
                + reason
                + "\r\nContent-Type: "
                + contentType
                + "\r\nCache-Control: no-store"
                + "\r\nContent-Length: "
                + bodyBytes.length
                + "\r\nConnection: close\r\n\r\n";
        output.write(headers.getBytes(ISO_8859_1));
        if (!"HEAD".equalsIgnoreCase(method)) {
            output.write(bodyBytes);
        }
        output.flush();
    }

    private static void tunnelToMockServer(
            Socket clientSocket,
            InputStream clientInput,
            InitialRequest initialRequest,
            byte[] bufferedBody,
            int mockServerPort,
            ExecutorService executor)
            throws IOException {
        try (var mockServerSocket = new Socket("127.0.0.1", mockServerPort)) {
            mockServerSocket.setTcpNoDelay(true);
            var mockServerOutput = mockServerSocket.getOutputStream();
            mockServerOutput.write(forwardedInitialBytes(initialRequest));
            mockServerOutput.write(bufferedBody);
            mockServerOutput.flush();

            var clientToMockServer = executor.submit(() -> copy(clientInput, mockServerOutput));
            try {
                copy(mockServerSocket.getInputStream(), clientSocket.getOutputStream());
            } finally {
                closeQuietly(clientSocket);
                closeQuietly(mockServerSocket);
                clientToMockServer.cancel(true);
            }
        }
    }

    private static void copy(InputStream input, OutputStream output) {
        var buffer = new byte[16 * 1024];
        try {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                output.flush();
            }
        } catch (IOException e) {
            if (!isExpectedSocketClose(e)) {
                e.printStackTrace(System.err);
            }
        }
    }

    private static boolean isConnectionHeader(String line) {
        var colon = line.indexOf(':');
        if (colon < 1) {
            return false;
        }
        return CONNECTION_HEADERS.contains(line.substring(0, colon).trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isExpectedSocketClose(IOException e) {
        var message = e.getMessage();
        return message != null
                && (message.contains("Broken pipe")
                        || message.contains("Connection reset")
                        || message.contains("Socket closed"));
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static Map<String, String> systemProperties() {
        var properties = new LinkedHashMap<String, String>();
        System.getProperties().stringPropertyNames().stream()
                .filter(name -> name.startsWith("mockserver."))
                .sorted()
                .forEach(name -> properties.put(name, System.getProperty(name)));
        return properties;
    }

    private static Map<String, OpenApiScenarioDocs.StaticContent> docsByPath(
            List<OpenApiScenarioDocs.StaticContent> docs) {
        var byPath = new LinkedHashMap<String, OpenApiScenarioDocs.StaticContent>();
        docs.forEach(content -> byPath.put(content.path(), content));
        return byPath;
    }

    private static String configuredValue(String property, String env, String defaultValue) {
        var propertyValue = System.getProperty(property);
        if (hasText(propertyValue)) {
            return propertyValue;
        }

        var envValue = System.getenv(env);
        if (hasText(envValue)) {
            return envValue;
        }

        if (defaultValue == null) {
            throw new OpenApiScenarioException(
                    "OpenAPI scenario spec path is required. Set system property "
                            + property
                            + " or environment variable "
                            + env
                            + ".");
        }
        return defaultValue;
    }

    private static int configuredInt(String env, int defaultValue) {
        var value = System.getenv(env);
        if (!hasText(value)) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new OpenApiScenarioException("Invalid integer value for environment variable " + env + ": " + value, e);
        }
    }

    private static String normalizedDocsPath(String docsPath) {
        if (!hasText(docsPath)) {
            return OpenApiScenarioDocs.DEFAULT_DOCS_PATH;
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String javaExecutable() {
        var executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    record InitialRequest(byte[] bytes, String method, String target, String path, Map<String, List<String>> headers) {

        static InitialRequest parse(byte[] bytes) {
            var headerText = new String(bytes, ISO_8859_1);
            var lines = headerText.split("\\r?\\n");
            if (lines.length == 0 || lines[0].isBlank()) {
                throw new OpenApiScenarioException("HTTP request line is missing.");
            }

            var requestParts = lines[0].split(" ", 3);
            if (requestParts.length < 2) {
                throw new OpenApiScenarioException("Invalid HTTP request line: " + lines[0]);
            }

            var headers = new LinkedHashMap<String, List<String>>();
            Arrays.stream(lines)
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .forEach(line -> {
                        var colon = line.indexOf(':');
                        if (colon > 0) {
                            headers.computeIfAbsent(
                                            line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                                            ignored -> new java.util.ArrayList<>())
                                    .add(line.substring(colon + 1).trim());
                        }
                    });

            return new InitialRequest(
                    bytes,
                    requestParts[0],
                    requestParts[1],
                    pathFromTarget(requestParts[1]),
                    headers);
        }

        String headerText() {
            return new String(bytes, ISO_8859_1);
        }

        boolean isWebSocketUpgrade() {
            return header("upgrade").stream().anyMatch(value -> "websocket".equalsIgnoreCase(value))
                    && header("connection").stream()
                            .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains("upgrade"));
        }

        private List<String> header(String name) {
            return headers.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
        }

        private int contentLength() {
            var values = header("content-length");
            if (values.isEmpty()) {
                return 0;
            }

            try {
                var contentLength = Integer.parseInt(values.get(0));
                if (contentLength < 0) {
                    throw new OpenApiScenarioException("Content-Length must not be negative.");
                }
                return contentLength;
            } catch (NumberFormatException e) {
                throw new OpenApiScenarioException("Invalid Content-Length header: " + values.get(0), e);
            }
        }

        private static String pathFromTarget(String target) {
            try {
                var uri = URI.create(target);
                if (hasText(uri.getRawPath())) {
                    return uri.getRawPath();
                }
            } catch (IllegalArgumentException ignored) {
            }

            var queryStart = target.indexOf('?');
            var path = queryStart >= 0 ? target.substring(0, queryStart) : target;
            return path.isBlank() ? "/" : path;
        }
    }

    private static final class Shutdown implements Runnable {

        private final ServerSocket serverSocket;
        private final ExecutorService executor;
        private final Set<Socket> activeSockets;
        private final Process mockServer;
        private final AtomicBoolean stopped = new AtomicBoolean();

        private Shutdown(
                ServerSocket serverSocket,
                ExecutorService executor,
                Set<Socket> activeSockets,
                Process mockServer) {
            this.serverSocket = serverSocket;
            this.executor = executor;
            this.activeSockets = activeSockets;
            this.mockServer = mockServer;
        }

        boolean isStopped() {
            return stopped.get();
        }

        @Override
        public void run() {
            if (!stopped.compareAndSet(false, true)) {
                return;
            }

            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            activeSockets.forEach(OpenApiScenarioProxy::closeQuietly);
            stopMockServer();
            executor.shutdownNow();
        }

        private void stopMockServer() {
            if (!mockServer.isAlive()) {
                return;
            }

            mockServer.destroy();
            try {
                if (!mockServer.waitFor(MOCKSERVER_SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    mockServer.destroyForcibly();
                    mockServer.waitFor(MOCKSERVER_FORCE_SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                mockServer.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }
}
