package org.jenkinsci.plugins.pipeline.github;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.lang.GroovyObjectSupport;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Date;
import java.util.Objects;

import static org.jenkinsci.plugins.pipeline.github.Snapshots.copy;

/**
 * Groovy wrapper over a pull request review comment or a commit comment.
 *
 * <p>Additionally provides the ability to update the comment body and delete the comment. Review comments and
 * commit comments live under different REST endpoints; the egit version edited and deleted every comment through
 * the commit comment endpoint, which failed for review comments.
 *
 * <p>Read and written through {@link GitHubRest}: hub4j can't edit, delete or reply to a comment by id, and its
 * {@code GHCommitComment} lacks the position fields.
 *
 * @author Aaron Whiteside
 */
public class ReviewCommentGroovyObject extends GroovyObjectSupport implements Serializable {
    private static final long serialVersionUID = 2L;

    enum Kind { PULL_REQUEST, COMMIT }

    private final String jobId;
    private final Kind kind;
    private final long id;
    private Integer line;
    private Integer position;
    private Integer originalPosition;
    private String commitId;
    private String originalCommitId;
    private String path;
    private String diffHunk;
    private Date createdAt;
    private Date updatedAt;
    private String body;
    private String url;
    private String user;
    private long inReplyToId;
    private String pullRequestUrl;
    private long pullRequestReviewId;

    /** Credentials set by {@code pullRequest.setCredentials}; never persisted. */
    @CheckForNull
    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED",
            justification = "Credentials must not be persisted; a resumed build uses the scan credentials")
    private transient StandardUsernamePasswordCredentials credentials;

    ReviewCommentGroovyObject(final String jobId,
                              final Kind kind,
                              final JsonNode comment,
                              @CheckForNull final StandardUsernamePasswordCredentials credentials) {
        Objects.requireNonNull(comment, "comment cannot be null");
        this.jobId = Objects.requireNonNull(jobId, "jobId cannot be null");
        this.kind = Objects.requireNonNull(kind, "kind cannot be null");
        this.id = GitHubRest.number(comment, "id");
        this.credentials = credentials;
        update(comment);
    }

    private void update(final JsonNode comment) {
        this.line = GitHubRest.integer(comment, "line");
        this.position = GitHubRest.integer(comment, "position");
        this.originalPosition = GitHubRest.integer(comment, "original_position");
        this.commitId = GitHubRest.text(comment, "commit_id");
        this.originalCommitId = GitHubRest.text(comment, "original_commit_id");
        this.path = GitHubRest.text(comment, "path");
        this.diffHunk = GitHubRest.text(comment, "diff_hunk");
        this.createdAt = GitHubRest.date(comment, "created_at");
        this.updatedAt = GitHubRest.date(comment, "updated_at");
        this.body = GitHubRest.text(comment, "body");
        this.url = GitHubRest.text(comment, "url");
        this.user = GitHubRest.login(comment, "user");
        this.inReplyToId = GitHubRest.number(comment, "in_reply_to_id");
        this.pullRequestUrl = GitHubRest.text(comment, "pull_request_url");
        this.pullRequestReviewId = GitHubRest.number(comment, "pull_request_review_id");
    }

    static String commentPath(final GitHubConnection connection, final Kind kind, final long id) {
        return connection.repoPath() + (kind == Kind.PULL_REQUEST ? "/pulls/comments/" : "/comments/") + id;
    }

    static JsonNode edit(final GitHubConnection connection, final Kind kind, final long id, final String body)
            throws IOException {
        return connection.rest().patch(commentPath(connection, kind, id),
                Collections.singletonMap("body", body), GitHubRest.ACCEPT_JSON);
    }

    static void delete(final GitHubConnection connection, final Kind kind, final long id) throws IOException {
        connection.rest().delete(commentPath(connection, kind, id), null);
    }

    /** {@code POST /repos/{owner}/{repo}/pulls/{pull_number}/comments/{comment_id}/replies}. */
    static JsonNode reply(final GitHubConnection connection, final int pullRequest, final long id,
                          final String body) throws IOException {
        return connection.rest().post(
                connection.repoPath() + "/pulls/" + pullRequest + "/comments/" + id + "/replies",
                Collections.singletonMap("body", body), GitHubRest.ACCEPT_JSON);
    }

    private GitHubConnection connect() throws IOException {
        return GitHubConnection.open(GitHubHelper.getJob(jobId), credentials);
    }

    /** Deprecated by GitHub in favour of position. */
    @Whitelisted
    @Deprecated
    public Integer getLine() {
        return line;
    }

    @Whitelisted
    public Integer getPosition() {
        return position;
    }

    @Whitelisted
    public Integer getOriginalPosition() {
        return originalPosition;
    }

    @Whitelisted
    public String getCommitId() {
        return commitId;
    }

    @Whitelisted
    public String getOriginalCommitId() {
        return originalCommitId;
    }

    @Whitelisted
    public String getPath() {
        return path;
    }

    @Whitelisted
    public String getDiffHunk() {
        return diffHunk;
    }

    @Whitelisted
    public Date getCreatedAt() {
        return copy(createdAt);
    }

    @Whitelisted
    public Date getUpdatedAt() {
        return copy(updatedAt);
    }

    @Whitelisted
    public String getBody() {
        return body;
    }

    @Whitelisted
    public long getId() {
        return id;
    }

    @Whitelisted
    public String getUrl() {
        return url;
    }

    @Whitelisted
    public String getUser() {
        return user;
    }

    @Whitelisted
    public long getInReplyToId() {
        return inReplyToId;
    }

    @Whitelisted
    public String getPullRequestUrl() {
        return pullRequestUrl;
    }

    @Whitelisted
    public long getPullRequestReviewId() {
        return pullRequestReviewId;
    }

    @Whitelisted
    public void setBody(final String body) {
        Objects.requireNonNull(body, "body is a required argument");
        try (GitHubConnection connection = connect()) {
            update(edit(connection, kind, id, body));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Whitelisted
    public void delete() {
        try (GitHubConnection connection = connect()) {
            delete(connection, kind, id);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Not whitelisted, as before.
    public void replyTo(final String body) {
        Objects.requireNonNull(body, "body is a required argument");
        if (kind != Kind.PULL_REQUEST || pullRequestUrl == null) {
            throw new UnsupportedOperationException("Only pull request review comments can be replied to");
        }
        int pullRequest = Integer.parseInt(pullRequestUrl.substring(pullRequestUrl.lastIndexOf('/') + 1));
        try (GitHubConnection connection = connect()) {
            reply(connection, pullRequest, id, body);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
