package org.jenkinsci.plugins.pipeline.github;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Job;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.User;
import org.jenkinsci.plugins.github_branch_source.Connector;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.github_branch_source.PullRequestSCMHead;
import org.jenkinsci.plugins.pipeline.github.client.ExtendedGitHubClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

/**
 * Various utility methods to obtain clients, repos and pull request scm heads from Jobs
 *
 * @author Aaron Whiteside
 */
public class GitHubHelper {

    private final static Logger LOG = LoggerFactory.getLogger(GitHubHelper.class);

    private GitHubHelper() {
        // go away
    }

    /**
     * Whether {@code login} is a collaborator on the job's repository. GitHub answers 204 for a collaborator and
     * 404 otherwise; hub4j's {@code GHRepository.isCollaborator} needs a {@code GHUser}, which costs another call.
     */
    public static Boolean isAuthorized(@NonNull final Job<?,?> job, @NonNull final String login) {
        try (GitHubConnection connection = GitHubConnection.open(job)) {
            connection.rest().get(connection.repoPath() + "/collaborators/" + login, GitHubRest.ACCEPT_JSON);
            return true;
        } catch (final IOException | RuntimeException e) {
            LOG.debug("Received an exception while trying to check if user {} is a collaborator of job: {}",
                    login, job.getFullName(), e);
            return false;
        }
    }

    /** @deprecated egit is no longer published; use hub4j/github-api. Removed with the egit dependency. */
    @Deprecated
    public static ExtendedGitHubClient getGitHubClient(@NonNull final Job<?,?> job) {
        SCMSource scmSource = SCMSource.SourceByItem.findSource(job);
        if (scmSource instanceof GitHubSCMSource) {
            GitHubSCMSource gitHubSource = (GitHubSCMSource) scmSource;

            URI uri = URI.create(gitHubSource.getApiUri());
            ExtendedGitHubClient client = new ExtendedGitHubClient(uri.getHost(), uri.getPort(), uri.getScheme());

            // configure credentials
            if (gitHubSource.getCredentialsId() != null) {
                StandardCredentials credentials = Connector.lookupScanCredentials(
                        job, gitHubSource.getApiUri(), gitHubSource.getCredentialsId(), gitHubSource.getRepoOwner());

                if (credentials instanceof StandardUsernamePasswordCredentials) {
                    StandardUsernamePasswordCredentials c = (StandardUsernamePasswordCredentials) credentials;
                    String userName = c.getUsername();
                    String password = c.getPassword().getPlainText();
                    client.setCredentials(userName, password);
                }
            }
            return client;
        }
        throw new IllegalArgumentException("Job's SCM is not GitHub.");
    }

    /** @deprecated egit is no longer published; use hub4j/github-api. Removed with the egit dependency. */
    @Deprecated
    public static RepositoryId getRepositoryId(@NonNull final Job<?,?> job) {
        SCMSource src = SCMSource.SourceByItem.findSource(job);
        if (src instanceof GitHubSCMSource) {
            GitHubSCMSource source = (GitHubSCMSource) src;
            return RepositoryId.create(source.getRepoOwner(), source.getRepository());
        }
        return null;
    }

    public static PullRequestSCMHead getPullRequest(@NonNull final Job job) throws Exception {
        PullRequestSCMHead head = (PullRequestSCMHead) SCMHead.HeadByItem.findHead(job);
        if (head == null) {
            throw new IllegalStateException("Build is not a Pull Request");
        }
        return head;
    }

    /** @deprecated egit is no longer published; use hub4j/github-api. Removed with the egit dependency. */
    @Deprecated
    public static String userToLogin(final User user) {
        return user == null ? null : user.getLogin();
    }

    public static Job getJob(final String jobId) {
        final Job job = Jenkins.get().getItemByFullName(jobId, Job.class);
        if (job == null) {
            throw new IllegalStateException("Unable to find Job: " + jobId);
        }
        return job;
    }

}
