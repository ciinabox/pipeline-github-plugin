package org.jenkinsci.plugins.pipeline.github;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Job;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.github_branch_source.PullRequestSCMHead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Utility methods to find jobs and pull request SCM heads, and to check collaborators
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

    public static PullRequestSCMHead getPullRequest(@NonNull final Job job) throws Exception {
        PullRequestSCMHead head = (PullRequestSCMHead) SCMHead.HeadByItem.findHead(job);
        if (head == null) {
            throw new IllegalStateException("Build is not a Pull Request");
        }
        return head;
    }

    public static Job getJob(final String jobId) {
        final Job job = Jenkins.get().getItemByFullName(jobId, Job.class);
        if (job == null) {
            throw new IllegalStateException("Unable to find Job: " + jobId);
        }
        return job;
    }

}
