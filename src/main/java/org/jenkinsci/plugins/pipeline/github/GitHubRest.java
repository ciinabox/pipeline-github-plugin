package org.jenkinsci.plugins.pipeline.github;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ProxyConfiguration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Minimal REST access for the few GitHub endpoints hub4j/github-api has no API for (removing requested
 * reviewers, editing maintainer_can_modify, merging with a commit title, editing or deleting a comment by id).
 * Everything else goes through hub4j via {@link GitHubConnection}.
 */
final class GitHubRest {

    static final String ACCEPT_JSON = "application/vnd.github+json";
    /** Adds body_html and body_text to comment responses, as egit's default Accept header did. */
    static final String ACCEPT_FULL_JSON = "application/vnd.github.full+json";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final String apiUri;
    @CheckForNull
    private final StandardUsernamePasswordCredentials credentials;

    GitHubRest(@NonNull final String apiUri, @CheckForNull final StandardUsernamePasswordCredentials credentials) {
        this.apiUri = apiUri.endsWith("/") ? apiUri.substring(0, apiUri.length() - 1) : apiUri;
        this.credentials = credentials;
    }

    JsonNode get(final String path, final String accept) throws IOException {
        return send("GET", path, null, accept);
    }

    /** GETs every page of a list endpoint, following {@code Link: rel="next"}. */
    List<JsonNode> getAll(final String path, final String accept) throws IOException {
        List<JsonNode> items = new ArrayList<>();
        String next = apiUri + path + (path.contains("?") ? "&" : "?") + "per_page=100";
        while (next != null) {
            HttpResponse<byte[]> response = exchange("GET", URI.create(next), null, accept);
            JsonNode page = parse("GET", path, response);
            if (page != null && page.isArray()) {
                page.forEach(items::add);
            }
            next = nextLink(response.headers().firstValue("Link").orElse(null));
        }
        return items;
    }

    @CheckForNull
    static String nextLink(@CheckForNull final String link) {
        if (link == null) {
            return null;
        }
        for (String part : link.split(",")) {
            String[] segments = part.split(";");
            if (segments.length > 1 && segments[1].trim().equals("rel=\"next\"")) {
                String url = segments[0].trim();
                return url.substring(1, url.length() - 1);
            }
        }
        return null;
    }

    JsonNode post(final String path, final Object body, final String accept) throws IOException {
        return send("POST", path, body, accept);
    }

    JsonNode patch(final String path, final Object body, final String accept) throws IOException {
        return send("PATCH", path, body, accept);
    }

    JsonNode put(final String path, final Object body) throws IOException {
        return send("PUT", path, body, ACCEPT_JSON);
    }

    JsonNode delete(final String path, final Object body) throws IOException {
        return send("DELETE", path, body, ACCEPT_JSON);
    }

    /**
     * Sends a request and returns the parsed body, or {@code null} for an empty body.
     *
     * @throws GitHubRestException for any non-2xx response
     */
    @CheckForNull
    JsonNode send(final String method, final String path, @CheckForNull final Object body, final String accept)
            throws IOException {
        return parse(method, path, exchange(method, URI.create(apiUri + path), body, accept));
    }

    @CheckForNull
    private static JsonNode parse(final String method, final String path, final HttpResponse<byte[]> response)
            throws IOException {
        int status = response.statusCode();
        byte[] bytes = response.body();
        if (status < 200 || status >= 300) {
            throw new GitHubRestException(method, path, status,
                    bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8));
        }
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return MAPPER.readTree(bytes);
    }

    private HttpResponse<byte[]> exchange(final String method, final URI uri, @CheckForNull final Object body,
                                          final String accept) throws IOException {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(MAPPER.writeValueAsBytes(body));
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Accept", accept == null ? ACCEPT_JSON : accept)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .method(method, publisher);
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (credentials != null) {
            String userPass = credentials.getUsername() + ":" + credentials.getPassword().getPlainText();
            request.header("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(userPass.getBytes(StandardCharsets.UTF_8)));
        }
        HttpClient client = ProxyConfiguration.newHttpClientBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        try {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted calling " + method + " " + uri.getPath(), e);
        }
    }

    // Helpers for reading snapshots out of REST responses.

    @CheckForNull
    static String text(final JsonNode node, final String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    static long number(final JsonNode node, final String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? 0L : value.asLong();
    }

    @CheckForNull
    static Integer integer(final JsonNode node, final String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    @CheckForNull
    static Date date(final JsonNode node, final String field) {
        String value = text(node, field);
        return value == null ? null : Date.from(Instant.parse(value));
    }

    @CheckForNull
    static String login(final JsonNode node, final String field) {
        JsonNode user = node.get(field);
        return user == null || user.isNull() ? null : text(user, "login");
    }

    /** A non-2xx response from the GitHub REST API. */
    static final class GitHubRestException extends IOException {
        private static final long serialVersionUID = 1L;
        private final int status;

        GitHubRestException(final String method, final String path, final int status, final String body) {
            super(String.format("%s %s: HTTP %d %s", method, path, status, message(body)));
            this.status = status;
        }

        int getStatus() {
            return status;
        }

        private static String message(final String body) {
            try {
                JsonNode node = MAPPER.readTree(body);
                if (node != null && node.hasNonNull("message")) {
                    return node.get("message").asText();
                }
            } catch (final IOException ignored) {
                // not JSON
            }
            return body;
        }
    }
}
