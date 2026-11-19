package org.jenkinsci.plugins.pipeline.github;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Job;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.github_branch_source.Connector;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;

/**
 * A hub4j {@link GitHub} for a job's {@link GitHubSCMSource}, obtained from github-branch-source's
 * {@link Connector} so it uses the source's API URI, scan credentials (including GitHub App credentials, whose
 * installation token is refreshed as needed), the Jenkins proxy and the configured rate limit strategy.
 *
 * <p>Open one per operation and close it when done: closing releases the connection back to the
 * {@link Connector}'s cache. hub4j objects must not be kept after closing, so the Groovy objects keep plain
 * serialisable snapshots and look objects up again when they need to call GitHub.
 */
final class GitHubConnection implements AutoCloseable {

    private final GitHub gitHub;
    private final String apiUri;
    private final String repoFullName;
    @CheckForNull
    private final StandardUsernamePasswordCredentials credentials;
    private GHRepository repository;

    private GitHubConnection(final GitHub gitHub,
                             final String apiUri,
                             final String repoFullName,
                             @CheckForNull final StandardUsernamePasswordCredentials credentials) {
        this.gitHub = gitHub;
        this.apiUri = apiUri;
        this.repoFullName = repoFullName;
        this.credentials = credentials;
    }

    /**
     * Connects with the job's scan credentials, or with {@code override} when the Pipeline set its own via
     * {@code pullRequest.setCredentials}.
     */
    static GitHubConnection open(@NonNull final Job<?, ?> job,
                                 @CheckForNull final StandardUsernamePasswordCredentials override)
            throws IOException {
        SCMSource scmSource = SCMSource.SourceByItem.findSource(job);
        if (!(scmSource instanceof GitHubSCMSource)) {
            throw new IllegalArgumentException("Job's SCM is not GitHub.");
        }
        GitHubSCMSource source = (GitHubSCMSource) scmSource;
        String apiUri = source.getApiUri();
        StandardCredentials credentials = override;
        if (credentials == null && source.getCredentialsId() != null) {
            credentials = Connector.lookupScanCredentials(
                    job, apiUri, source.getCredentialsId(), source.getRepoOwner());
        }
        GitHub gitHub = Connector.connect(apiUri, credentials);
        return new GitHubConnection(gitHub,
                apiUri,
                source.getRepoOwner() + "/" + source.getRepository(),
                credentials instanceof StandardUsernamePasswordCredentials
                        ? (StandardUsernamePasswordCredentials) credentials
                        : null);
    }

    static GitHubConnection open(@NonNull final Job<?, ?> job) throws IOException {
        return open(job, null);
    }

    GitHub gitHub() {
        return gitHub;
    }

    /** owner/name of the job's repository. */
    String repoFullName() {
        return repoFullName;
    }

    /** The job's repository, fetched once per connection. */
    GHRepository repository() throws IOException {
        if (repository == null) {
            repository = gitHub.getRepository(repoFullName);
        }
        return repository;
    }

    /** REST access for endpoints hub4j has no API for, with the same credentials. */
    GitHubRest rest() {
        return new GitHubRest(apiUri, credentials);
    }

    /** {@code /repos/owner/name}, for {@link #rest()} paths. */
    String repoPath() {
        return "/repos/" + repoFullName;
    }

    @Override
    public void close() {
        Connector.release(gitHub);
    }
}
