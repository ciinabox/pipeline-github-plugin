package org.jenkinsci.plugins.pipeline.github;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import hudson.model.Item;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class GitHubHelperTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule();

    public static class TestSourceByItemImpl extends SCMSource.SourceByItem {
        private final int port;

        public TestSourceByItemImpl(int port) {
            this.port = port;
        }

        @Override
        public SCMSource getSource(Item item) {
            GitHubSCMSource result = new GitHubSCMSource("owner", "repo", null, false);
            result.setCredentialsId("credentialsId");
            result.setApiUri(String.format("http://localhost:%d", port));
            return result;
        }
    }

    @Test
    public void collaboratorIsAuthorizedWithTheScanCredentials() throws Exception {
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(),
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "credentialsId", null, "user", "token"));
        WorkflowJob job = r.createProject(WorkflowJob.class, "p");
        r.jenkins.getExtensionList(SCMSource.SourceByItem.class).add(new TestSourceByItemImpl(wireMockRule.port()));
        GitHubApiStubs.stubApiRoot(wireMockRule.port());
        stubFor(get(urlPathEqualTo("/repos/owner/repo/collaborators/alice"))
                .willReturn(aResponse().withStatus(204)));
        stubFor(get(urlPathEqualTo("/repos/owner/repo/collaborators/mallory"))
                .willReturn(aResponse().withStatus(404).withBody("{\"message\":\"Not Found\"}")));

        // the call isAuthorized makes, without its catch-all, so a failure shows its cause
        try (GitHubConnection connection = GitHubConnection.open(job)) {
            connection.rest().get(connection.repoPath() + "/collaborators/alice", GitHubRest.ACCEPT_JSON);
        }
        assertTrue(GitHubHelper.isAuthorized(job, "alice"));
        assertFalse(GitHubHelper.isAuthorized(job, "mallory"));

        String basic = "Basic " + Base64.getEncoder().encodeToString("user:token".getBytes(StandardCharsets.UTF_8));
        verify(getRequestedFor(urlPathEqualTo("/repos/owner/repo/collaborators/alice"))
                .withHeader("Authorization", equalTo(basic)));
    }
}
