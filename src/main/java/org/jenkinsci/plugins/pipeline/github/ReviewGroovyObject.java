package org.jenkinsci.plugins.pipeline.github;

import groovy.lang.GroovyObjectSupport;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewState;

import java.io.Serializable;
import java.util.Objects;

import static org.jenkinsci.plugins.pipeline.github.Snapshots.login;
import static org.jenkinsci.plugins.pipeline.github.Snapshots.unchecked;

/**
 * Groovy wrapper over a pull request review.
 *
 * @author Aaron Whiteside
 */
public class ReviewGroovyObject extends GroovyObjectSupport implements Serializable {
    private static final long serialVersionUID = 2L;

    private final String user;
    private final String body;
    private final String commitId;
    private final long id;
    private final String state;

    ReviewGroovyObject(final GHPullRequestReview review) {
        Objects.requireNonNull(review, "review cannot be null");
        this.user = unchecked(() -> login(review.getUser()));
        this.body = review.getBody();
        this.commitId = review.getCommitId();
        this.id = review.getId();
        // the REST API's upper-case state, as before: APPROVED, CHANGES_REQUESTED, COMMENTED, DISMISSED, PENDING
        GHPullRequestReviewState reviewState = review.getState();
        this.state = reviewState == null ? null : reviewState.name();
    }

    @Whitelisted
    public String getUser() {
        return user;
    }

    @Whitelisted
    public String getBody() {
        return body;
    }

    @Whitelisted
    public String getCommitId() {
        return commitId;
    }

    @Whitelisted
    public long getId() {
        return id;
    }

    @Whitelisted
    public String getState() {
        return state;
    }
}
