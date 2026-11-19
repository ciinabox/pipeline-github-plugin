package org.jenkinsci.plugins.pipeline.github;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import hudson.model.Item;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.github_branch_source.PullRequestSCMHead;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Aaron Whiteside
 */
public class PullRequestGroovyObjectTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule();

    public static class TestHeadByItemImpl extends SCMHead.HeadByItem {
        @Override
        public SCMHead getHead(Item item) {
            return new PullRequestSCMHead("PR-42", "owner", "repo", null, 42, null, null, null);
        }
    }

    public static class TestSourceByItemImpl extends SCMSource.SourceByItem {
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

        private int port;
    }

    private WorkflowJob job;

    private String fixture(final String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("PORT", Integer.toString(wireMockRule.port()));
        }
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(final String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    @Before
    public void setUp() throws Exception {
        job = r.createProject(WorkflowJob.class, "p");
        r.jenkins.getExtensionList(SCMHead.HeadByItem.class).add(new TestHeadByItemImpl());
        r.jenkins.getExtensionList(SCMSource.SourceByItem.class).add(new TestSourceByItemImpl(wireMockRule.port()));
        GitHubApiStubs.stubApiRoot(wireMockRule.port());
        stubFor(get(urlPathEqualTo("/repos/owner/repo")).willReturn(json(String.format(
                "{\"id\":1,\"name\":\"repo\",\"full_name\":\"owner/repo\",\"owner\":{\"login\":\"owner\"},"
                        + "\"url\":\"http://localhost:%d/repos/owner/repo\"}", wireMockRule.port()))));
        stubFor(get(urlPathEqualTo("/repos/owner/repo/pulls/42")).willReturn(json(fixture("pull-42.json"))));
    }

    @Test
    public void testPullRequestBaseSha() throws Exception {
        PullRequestGroovyObject prgo = new PullRequestGroovyObject(job);
        assertEquals("base456", prgo.getBaseSha());
    }

    @Test
    public void snapshotKeepsTheValuesPipelinesSee() throws Exception {
        PullRequestGroovyObject pr = new PullRequestGroovyObject(job);

        assertEquals(1001L, pr.getId());
        assertEquals(42, pr.getNumber());
        assertEquals("https://github.com/owner/repo/pull/42", pr.getUrl());
        assertEquals("https://github.com/owner/repo/pull/42.diff", pr.getDiffUrl());
        assertEquals("open", pr.getState());
        assertEquals("Add a thing", pr.getTitle());
        assertEquals("The body", pr.getBody());
        assertTrue(pr.isDraft());
        assertFalse(pr.isLocked());
        assertEquals("head123", pr.getHead());
        assertEquals("feature", pr.getHeadRef());
        assertEquals("main", pr.getBase());
        assertEquals("octocat", pr.getCreatedBy());
        assertNull(pr.getClosedAt());
        assertNull(pr.getMergedBy());
        assertEquals(3, pr.getCommitCount());
        assertEquals(2, pr.getCommentCount());
        assertEquals(10, pr.getAdditions());
        assertEquals(4, pr.getDeletions());
        assertEquals(2, pr.getChangedFiles());
        assertEquals("mmm111", pr.getMergeCommitSha());
        assertEquals("clean", pr.getMergeableState());
        assertTrue(pr.isMergeable());
        assertFalse(pr.isMerged());
        assertTrue(pr.isMaintainerCanModify());
        assertThat(list(pr.getAssignees()), contains("alice", "bob"));
        assertEquals("v1.0", pr.getMilestone().getTitle());
        assertEquals("open", pr.getMilestone().getState());
        assertEquals(7, pr.getMilestone().getNumber());
        assertEquals("octocat", pr.getMilestone().getCreator());
        assertThat(list(pr.getRequestedReviewers()), contains("carol"));
        assertThat(list(pr.getRequestedTeamReviewers()), contains("Platform"));
    }

    @Test
    public void serialisesWithoutHub4jObjects() throws Exception {
        PullRequestGroovyObject pr = new PullRequestGroovyObject(job);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(pr);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            PullRequestGroovyObject copy = (PullRequestGroovyObject) in.readObject();
            assertEquals("Add a thing", copy.getTitle());
            assertEquals("v1.0", copy.getMilestone().getTitle());
        }
    }

    @Test
    public void commentKeepsBodyHtml() throws Exception {
        stubFor(post(urlPathEqualTo("/repos/owner/repo/issues/42/comments"))
                .withHeader("Accept", equalTo(GitHubRest.ACCEPT_FULL_JSON))
                .willReturn(json("{\"id\":9,\"body\":\"hi\",\"body_html\":\"<p>hi</p>\",\"body_text\":\"hi\","
                        + "\"user\":{\"login\":\"bot\"},\"created_at\":\"2026-03-03T00:00:00Z\"}")));

        IssueCommentGroovyObject comment = new PullRequestGroovyObject(job).comment("hi");

        assertEquals(9L, comment.getId());
        assertEquals("<p>hi</p>", comment.getBodyHtml());
        assertEquals("bot", comment.getUser());
        verify(postRequestedFor(urlPathEqualTo("/repos/owner/repo/issues/42/comments"))
                .withRequestBody(equalToJson("{\"body\":\"hi\"}")));
    }

    @Test
    public void mergeSendsTheCommitTitleAndReturnsTheSha() throws Exception {
        stubFor(put(urlPathEqualTo("/repos/owner/repo/pulls/42/merge"))
                .willReturn(json("{\"sha\":\"merged789\",\"merged\":true,\"message\":\"Pull Request successfully merged\"}")));

        String sha = new PullRequestGroovyObject(job).merge("Title", "Message", "head123", "squash");

        assertEquals("merged789", sha);
        verify(putRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls/42/merge"))
                .withRequestBody(equalToJson("{\"commit_title\":\"Title\",\"commit_message\":\"Message\","
                        + "\"sha\":\"head123\",\"merge_method\":\"squash\"}")));
    }

    @Test
    public void mergeFailureNamesTheStatus() throws Exception {
        stubFor(put(urlPathEqualTo("/repos/owner/repo/pulls/42/merge"))
                .willReturn(aResponse().withStatus(405).withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Pull Request is not mergeable\"}")));

        PullRequestGroovyObject pr = new PullRequestGroovyObject(job);
        Exception e = assertThrows(Exception.class, () -> pr.merge("Message"));

        assertThat(e.getMessage(), containsString("Pull Request is not mergeable (405)"));
    }

    @Test
    public void deleteReviewRequestsUsesTheRestApi() throws Exception {
        stubFor(delete(urlPathEqualTo("/repos/owner/repo/pulls/42/requested_reviewers"))
                .willReturn(json(fixture("pull-42.json"))));

        PullRequestGroovyObject pr = new PullRequestGroovyObject(job);
        pr.deleteReviewRequests(Arrays.asList("carol"));
        pr.deleteTeamReviewRequest("platform");

        verify(deleteRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls/42/requested_reviewers"))
                .withRequestBody(equalToJson("{\"reviewers\":[\"carol\"]}")));
        verify(deleteRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls/42/requested_reviewers"))
                .withRequestBody(equalToJson("{\"team_reviewers\":[\"platform\"]}")));
    }

    @Test
    public void setMaintainerCanModifyPatchesThePullRequest() throws Exception {
        stubFor(patch(urlPathEqualTo("/repos/owner/repo/pulls/42")).willReturn(json(fixture("pull-42.json"))));

        new PullRequestGroovyObject(job).setMaintainerCanModify(false);

        verify(patchRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls/42"))
                .withRequestBody(equalToJson("{\"maintainer_can_modify\":false}")));
    }

    @Test
    public void createStatusOnTheHeadCommit() throws Exception {
        stubFor(post(urlPathEqualTo("/repos/owner/repo/statuses/head123"))
                .willReturn(json("{\"id\":5,\"state\":\"success\",\"context\":\"ci\",\"description\":\"ok\","
                        + "\"target_url\":\"https://ci/1\",\"creator\":{\"login\":\"bot\"},"
                        + "\"created_at\":\"2026-03-03T00:00:00Z\",\"updated_at\":\"2026-03-03T00:00:00Z\"}")));

        CommitStatusGroovyObject status = new PullRequestGroovyObject(job)
                .createStatus("success", "ci", "ok", "https://ci/1");

        assertEquals("success", status.getState());
        assertEquals("ci", status.getContext());
        assertEquals("bot", status.getCreator());
        verify(postRequestedFor(urlPathEqualTo("/repos/owner/repo/statuses/head123"))
                .withRequestBody(containing("\"state\":\"success\"")));
    }

    @Test
    public void commitsCarryLoginsAndFetchStatsOnDemand() throws Exception {
        stubFor(get(urlPathEqualTo("/repos/owner/repo/pulls/42/commits")).willReturn(json(
                "[{\"sha\":\"c1\",\"url\":\"u\",\"author\":{\"login\":\"alice\"},\"committer\":{\"login\":\"web-flow\"},"
                        + "\"commit\":{\"message\":\"m\",\"comment_count\":1},\"parents\":[{\"sha\":\"p0\"}]}]")));
        stubFor(get(urlPathEqualTo("/repos/owner/repo/commits/c1")).willReturn(json(
                "{\"sha\":\"c1\",\"stats\":{\"additions\":5,\"deletions\":2,\"total\":7},"
                        + "\"files\":[{\"filename\":\"a.txt\",\"status\":\"modified\",\"additions\":5}]}")));

        CommitGroovyObject commit = new PullRequestGroovyObject(job).getCommits().iterator().next();

        assertEquals("alice", commit.getAuthor());
        assertEquals("web-flow", commit.getCommitter());
        assertThat(list(commit.getParents()), contains("p0"));
        assertEquals(5, commit.getAdditions());
        assertEquals(7, commit.getTotalChanges());
        assertEquals("a.txt", commit.getFiles().iterator().next().getFilename());
    }

    private static <T> List<T> list(final Iterable<T> items) {
        List<T> result = new ArrayList<>();
        items.forEach(result::add);
        return result;
    }
}
