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
 * Groovy wrapper over an issue (pull request conversation) comment.
 *
 * <p>Comments are read and written through {@link GitHubRest} with the {@code full+json} media type, so
 * {@link #getBodyHtml()} and {@link #getBodyText()} stay populated; hub4j's {@code GHIssueComment} has neither, and
 * no way to edit or delete a comment by id.
 *
 * @author Aaron Whiteside
 */
public class IssueCommentGroovyObject extends GroovyObjectSupport implements Serializable {
    private static final long serialVersionUID = 2L;

    private final String jobId;
    private final long id;
    private Date createdAt;
    private Date updatedAt;
    private String body;
    private String bodyHtml;
    private String bodyText;
    private String url;
    private String user;

    /** Credentials set by {@code pullRequest.setCredentials}; never persisted. */
    @CheckForNull
    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED",
            justification = "Credentials must not be persisted; a resumed build uses the scan credentials")
    private transient StandardUsernamePasswordCredentials credentials;

    IssueCommentGroovyObject(final String jobId,
                             final JsonNode comment,
                             @CheckForNull final StandardUsernamePasswordCredentials credentials) {
        Objects.requireNonNull(comment, "comment cannot be null");
        this.jobId = Objects.requireNonNull(jobId, "jobId cannot be null");
        this.id = GitHubRest.number(comment, "id");
        this.credentials = credentials;
        update(comment);
    }

    private void update(final JsonNode comment) {
        this.createdAt = GitHubRest.date(comment, "created_at");
        this.updatedAt = GitHubRest.date(comment, "updated_at");
        this.body = GitHubRest.text(comment, "body");
        this.bodyHtml = GitHubRest.text(comment, "body_html");
        this.bodyText = GitHubRest.text(comment, "body_text");
        this.url = GitHubRest.text(comment, "url");
        this.user = GitHubRest.login(comment, "user");
    }

    /** {@code PATCH /repos/{owner}/{repo}/issues/comments/{id}}. */
    static JsonNode edit(final GitHubConnection connection, final long id, final String body) throws IOException {
        return connection.rest().patch(connection.repoPath() + "/issues/comments/" + id,
                Collections.singletonMap("body", body), GitHubRest.ACCEPT_FULL_JSON);
    }

    /** {@code DELETE /repos/{owner}/{repo}/issues/comments/{id}}. */
    static void delete(final GitHubConnection connection, final long id) throws IOException {
        connection.rest().delete(connection.repoPath() + "/issues/comments/" + id, null);
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
    public String getBodyHtml() {
        return bodyHtml;
    }

    @Whitelisted
    public String getBodyText() {
        return bodyText;
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
    public void setBody(final String body) {
        Objects.requireNonNull(body, "body cannot be null");
        try (GitHubConnection connection = GitHubConnection.open(GitHubHelper.getJob(jobId), credentials)) {
            update(edit(connection, id, body));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Whitelisted
    public void delete() {
        try (GitHubConnection connection = GitHubConnection.open(GitHubHelper.getJob(jobId), credentials)) {
            delete(connection, id);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
