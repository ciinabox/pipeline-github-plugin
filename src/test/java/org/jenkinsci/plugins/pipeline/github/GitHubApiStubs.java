package org.jenkinsci.plugins.pipeline.github;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * Stubs github-branch-source's {@code Connector.connect} needs before any other call: it checks the API root
 * ({@code GET /} must name a rate_limit_url) and reads the rate limit.
 */
final class GitHubApiStubs {

    private GitHubApiStubs() {
    }

    static void stubApiRoot(final int port) {
        stubFor(get(urlPathEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("{\"rate_limit_url\":\"http://localhost:%d/rate_limit\"}", port))));
        long reset = System.currentTimeMillis() / 1000 + 3600;
        String limit = String.format("{\"limit\":5000,\"remaining\":4999,\"reset\":%d,\"used\":1}", reset);
        stubFor(get(urlPathEqualTo("/rate_limit"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("{\"resources\":{\"core\":%s,\"search\":%s,\"graphql\":%s,"
                                + "\"integration_manifest\":%s},\"rate\":%s}", limit, limit, limit, limit, limit))));
    }
}
