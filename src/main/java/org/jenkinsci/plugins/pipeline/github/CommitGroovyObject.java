package org.jenkinsci.plugins.pipeline.github;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.lang.GroovyObjectSupport;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.github.GHCommitState;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

/**
 * Groovy wrapper over a commit of a pull request.
 *
 * <p>Built from the pull request's commit list ({@code GET .../pulls/{n}/commits}), read through
 * {@link GitHubRest} because hub4j's {@code GHPullRequestCommitDetail} drops the author and committer logins.
 * That list has no stats or files, so the first call to {@link #getAdditions()}, {@link #getDeletions()},
 * {@link #getTotalChanges()} or {@link #getFiles()} fetches the commit (the egit version threw a
 * NullPointerException there).
 *
 * @author Aaron Whiteside
 */
public class CommitGroovyObject extends GroovyObjectSupport implements Serializable {
    private static final long serialVersionUID = 2L;

    private final String jobId;
    private final String sha;
    private final String url;
    private final String author;
    private final String committer;
    private final String message;
    private final int commentCount;
    private final List<String> parents;

    // Filled from GET .../commits/{sha} on first use.
    private boolean detailed;
    private int additions;
    private int deletions;
    private int totalChanges;
    private ArrayList<CommitFileGroovyObject> files;

    /** Credentials set by {@code pullRequest.setCredentials}; never persisted. */
    @CheckForNull
    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED",
            justification = "Credentials must not be persisted; a resumed build uses the scan credentials")
    private transient StandardUsernamePasswordCredentials credentials;

    CommitGroovyObject(final String jobId,
                       final JsonNode commit,
                       @CheckForNull final StandardUsernamePasswordCredentials credentials) {
        Objects.requireNonNull(commit, "commit cannot be null");
        this.jobId = Objects.requireNonNull(jobId, "jobId cannot be null");
        this.credentials = credentials;
        this.sha = GitHubRest.text(commit, "sha");
        this.url = GitHubRest.text(commit, "url");
        this.author = GitHubRest.login(commit, "author");
        this.committer = GitHubRest.login(commit, "committer");
        JsonNode gitCommit = commit.path("commit");
        this.message = GitHubRest.text(gitCommit, "message");
        this.commentCount = (int) GitHubRest.number(gitCommit, "comment_count");
        List<String> parentShas = new ArrayList<>();
        commit.path("parents").forEach(p -> parentShas.add(GitHubRest.text(p, "sha")));
        this.parents = parentShas;
        if (commit.has("stats") || commit.has("files")) {
            detail(commit);
        }
    }

    private GitHubConnection connect() throws IOException {
        return GitHubConnection.open(GitHubHelper.getJob(jobId), credentials);
    }

    private void detail(final JsonNode commit) {
        JsonNode stats = commit.path("stats");
        this.additions = (int) GitHubRest.number(stats, "additions");
        this.deletions = (int) GitHubRest.number(stats, "deletions");
        this.totalChanges = (int) GitHubRest.number(stats, "total");
        ArrayList<CommitFileGroovyObject> commitFiles = new ArrayList<>();
        commit.path("files").forEach(f -> commitFiles.add(new CommitFileGroovyObject(f)));
        this.files = commitFiles;
        this.detailed = true;
    }

    private synchronized void ensureDetailed() {
        if (detailed) {
            return;
        }
        try (GitHubConnection connection = connect()) {
            detail(connection.rest().get(connection.repoPath() + "/commits/" + sha, GitHubRest.ACCEPT_JSON));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Whitelisted
    public String getSha() {
        return sha;
    }

    @Whitelisted
    public String getUrl() {
        return url;
    }

    @Whitelisted
    public String getAuthor() {
        return author;
    }

    @Whitelisted
    public String getCommitter() {
        return committer;
    }

    @Whitelisted
    public String getMessage() {
        return message;
    }

    @Whitelisted
    public int getCommentCount() {
        return commentCount;
    }

    @Whitelisted
    public int getAdditions() {
        ensureDetailed();
        return additions;
    }

    @Whitelisted
    public int getDeletions() {
        ensureDetailed();
        return deletions;
    }

    @Whitelisted
    public int getTotalChanges() {
        ensureDetailed();
        return totalChanges;
    }

    @Whitelisted
    public Iterable<ReviewCommentGroovyObject> getComments() {
        try (GitHubConnection connection = connect()) {
            return connection.rest()
                    .getAll(connection.repoPath() + "/commits/" + sha + "/comments", GitHubRest.ACCEPT_JSON)
                    .stream()
                    .map(c -> new ReviewCommentGroovyObject(jobId, ReviewCommentGroovyObject.Kind.COMMIT, c,
                            credentials))
                    .collect(toList());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Whitelisted
    public Iterable<CommitStatusGroovyObject> getStatuses() {
        try (GitHubConnection connection = connect()) {
            return connection.repository().listCommitStatuses(sha).toList()
                    .stream()
                    .map(CommitStatusGroovyObject::new)
                    .collect(toList());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Whitelisted
    public Iterable<String> getParents() {
        return new ArrayList<>(parents);
    }

    @Whitelisted
    public Iterable<CommitFileGroovyObject> getFiles() {
        ensureDetailed();
        return new ArrayList<>(files);
    }

    @Whitelisted
    public ReviewCommentGroovyObject comment(final Map<String, Object> params) {
        Objects.requireNonNull(params.get("body"), "body is a required argument");
        return comment(params.get("body").toString(),
                       params.get("path") != null ? params.get("path").toString() : null,
                       (Integer) params.get("position"));
    }

    @Whitelisted
    public ReviewCommentGroovyObject comment(final String body,
                                             final String path,
                                             final Integer position) {
        Objects.requireNonNull(body, "body is a required argument");
        Map<String, Object> request = new HashMap<>();
        request.put("body", body);
        if (path != null) {
            request.put("path", path);
        }
        if (position != null) {
            request.put("position", position);
        }
        try (GitHubConnection connection = connect()) {
            JsonNode created = connection.rest().post(connection.repoPath() + "/commits/" + sha + "/comments",
                    request, GitHubRest.ACCEPT_JSON);
            return new ReviewCommentGroovyObject(jobId, ReviewCommentGroovyObject.Kind.COMMIT, created, credentials);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Whitelisted
    public CommitStatusGroovyObject createStatus(final Map<String, Object> params) {
        Objects.requireNonNull(params.get("status"), "status is a required argument");
        return createStatus(params.get("status").toString(),
                            params.get("context") != null ? params.get("context").toString() : null,
                            params.get("description") != null ? params.get("description").toString() : null,
                            params.get("targetUrl") != null ? params.get("targetUrl").toString() : null);
    }

    @Whitelisted
    public CommitStatusGroovyObject createStatus(final String status,
                                                 final String context,
                                                 final String description,
                                                 final String targetUrl) {
        Objects.requireNonNull(status, "status is a required argument");
        try (GitHubConnection connection = connect()) {
            return createStatus(connection, sha, status, context, description, targetUrl);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static CommitStatusGroovyObject createStatus(final GitHubConnection connection,
                                                 final String sha,
                                                 final String status,
                                                 final String context,
                                                 final String description,
                                                 final String targetUrl) throws IOException {
        GHCommitState state;
        try {
            state = GHCommitState.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "status must be one of error, failure, pending, success; was " + status, e);
        }
        return new CommitStatusGroovyObject(
                connection.repository().createCommitStatus(sha, state, targetUrl, description, context));
    }
}
