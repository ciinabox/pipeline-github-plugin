package org.jenkinsci.plugins.pipeline.github;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import com.fasterxml.jackson.databind.JsonNode;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.github_branch_source.PullRequestSCMRevision;
import org.kohsuke.github.GHPullRequest;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MergedPullRequestGlobalVariable extends GlobalVariable {
    private static final Logger LOG = LoggerFactory.getLogger(MergedPullRequestGlobalVariable.class);

    @NonNull
    @Override
    public String getName() {
        return "mergedPullRequest";
    }

    @NonNull
    @Override
    public Object getValue(@NonNull final CpsScript script) throws Exception {
        final Run<?, ?> build = script.$build();
        if (build == null) {
            throw new IllegalStateException("No associated build");
        }
        LOG.trace("initializing {}", MergedPullRequestGroovyObject.class);
        String commitHash = getCommitHash(build);
        if (commitHash != null) {
            try (GitHubConnection connection = GitHubConnection.open(build.getParent())) {
                // the snapshot may need to call GitHub, so build it while the connection is open
                return new MergedPullRequestGroovyObject(build.getParent(),
                        getMergedPullRequest(connection, commitHash));
            } catch (final IOException | RuntimeException e) {
                LOG.warn("Failed to query GitHub API for pull requests with merge commit {}; job = {}",
                        commitHash, build.getFullDisplayName(), e);
            }
        }
        return new MergedPullRequestGroovyObject(build.getParent(), null);
    }

    private String getCommitHash(final Run<?, ?> run) {
        SCMSource scmSource = SCMSource.SourceByItem.findSource(run.getParent());
        LOG.debug("Checking for merged pull request for run: {}", run.getFullDisplayName());
        if (null != scmSource && scmSource instanceof GitHubSCMSource) {
            LOG.debug("Found GitHubSCMSource for run: {}", run.getFullDisplayName());
            SCMRevision revision = SCMRevisionAction.getRevision(scmSource, run);
            if (null != revision) {
                LOG.debug("Found SCMRevision for run {}: {} - {}", run.getFullDisplayName(), revision, revision.getClass().getName());
                if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl) {
                    AbstractGitSCMSource.SCMRevisionImpl gitRevision = (AbstractGitSCMSource.SCMRevisionImpl) revision;
                    LOG.debug("Git revision hash for run {}: {}", run.getFullDisplayName(), gitRevision.getHash());
                    return gitRevision.getHash();
                } else if (revision instanceof PullRequestSCMRevision) {
                    PullRequestSCMRevision prRevision = (PullRequestSCMRevision) revision;
                    LOG.debug("PR revision for run {}: {}", run.getFullDisplayName(), prRevision.getPullHash());
                    return prRevision.getPullHash();
                }
            }
        }
        return null;
    }

    /**
     * The merged pull request whose merge commit is {@code commitHash}, from
     * {@code GET /repos/{owner}/{repo}/commits/{sha}/pulls}. Read through {@link GitHubRest}: hub4j would fetch the
     * commit first, and its list items fetch the whole pull request again to read merge_commit_sha.
     */
    private GHPullRequest getMergedPullRequest(final GitHubConnection connection, final String commitHash)
            throws IOException {
        LOG.debug("searching for merged PR in repo {}, commit {}", connection.repoFullName(), commitHash);
        for (JsonNode pr : connection.rest().getAll(
                connection.repoPath() + "/commits/" + commitHash + "/pulls", GitHubRest.ACCEPT_JSON)) {
            LOG.debug("Pull request #{}: mergedAt = {}, mergeCommitSha = {}", GitHubRest.number(pr, "number"),
                    GitHubRest.text(pr, "merged_at"), GitHubRest.text(pr, "merge_commit_sha"));
            if (GitHubRest.text(pr, "merged_at") != null && commitHash.equals(GitHubRest.text(pr, "merge_commit_sha"))) {
                return connection.repository().getPullRequest((int) GitHubRest.number(pr, "number"));
            }
        }
        return null;
    }

}
