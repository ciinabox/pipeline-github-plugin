package org.jenkinsci.plugins.pipeline.github;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.fasterxml.jackson.databind.JsonNode;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.lang.GroovyObjectSupport;
import hudson.model.Job;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReviewBuilder;
import org.kohsuke.github.GHPullRequestReviewEvent;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GHUser;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static java.util.stream.Collectors.toList;
import static org.jenkinsci.plugins.pipeline.github.Snapshots.copy;
import static org.jenkinsci.plugins.pipeline.github.Snapshots.login;
import static org.jenkinsci.plugins.pipeline.github.Snapshots.lower;
import static org.jenkinsci.plugins.pipeline.github.Snapshots.url;

/**
 * Groovy object that represents a GitHub PullRequest.
 *
 * <p>Holds a serialisable snapshot of the pull request, taken when the object is created and after every change
 * made through it (or {@link #refresh()}). Collections such as reviews, labels and comments are read from GitHub
 * on each call. Calls go through hub4j (via {@link GitHubConnection}) where it has an API, and through
 * {@link GitHubRest} for the endpoints it lacks.
 *
 * @author Aaron Whiteside
 */
@PersistIn(PersistenceContext.NONE)
public class PullRequestGroovyObject extends GroovyObjectSupport implements Serializable {

    private static final long serialVersionUID = 2L;

    private final String jobId;
    private int pullRequestNumber;

    // snapshot
    private long id;
    private String diffUrl;
    private String url;
    private String patchUrl;
    private String state;
    private String issueUrl;
    private String title;
    private String body;
    private boolean locked;
    private boolean draft;
    private MilestoneGroovyObject milestone;
    private String headSha;
    private String headRef;
    private String baseSha;
    private String baseRef;
    private Date updatedAt;
    private Date createdAt;
    private String createdBy;
    private Date closedAt;
    private String closedBy;
    private Date mergedAt;
    private String mergedBy;
    private int commitCount;
    private int commentCount;
    private int deletions;
    private String mergeCommitSha;
    private String mergeableState;
    private boolean maintainerCanModify;
    private int additions;
    private int changedFiles;
    private boolean mergeable;
    private boolean merged;
    private ArrayList<String> assignees = new ArrayList<>();

    private transient Job<?, ?> job;
    /** Credentials set by {@link #setCredentials}; never persisted, so lost if the build resumes. */
    @CheckForNull
    private transient StandardUsernamePasswordCredentials credentials;

    PullRequestGroovyObject(@NonNull final Job<?, ?> job) throws Exception {
        this.job = job;
        this.jobId = job.getFullName();
        this.pullRequestNumber = GitHubHelper.getPullRequest(job).getNumber();
        try (GitHubConnection connection = connect()) {
            snapshot(connection.repository().getPullRequest(pullRequestNumber));
        }
    }

    /** For a pull request already fetched; {@code pr} may be null (no merged pull request found). */
    PullRequestGroovyObject(@NonNull final Job<?, ?> job, @CheckForNull final GHPullRequest pr) throws Exception {
        this.job = job;
        this.jobId = job.getFullName();
        if (pr != null) {
            this.pullRequestNumber = pr.getNumber();
            snapshot(pr);
        }
    }

    private Job<?, ?> getJob() {
        if (job == null) {
            job = GitHubHelper.getJob(jobId);
        }
        return job;
    }

    private GitHubConnection connect() throws IOException {
        return GitHubConnection.open(getJob(), credentials);
    }

    private GHPullRequest fetch(final GitHubConnection connection) throws IOException {
        return connection.repository().getPullRequest(pullRequestNumber);
    }

    private String pullPath(final GitHubConnection connection) {
        return connection.repoPath() + "/pulls/" + pullRequestNumber;
    }

    private String issuePath(final GitHubConnection connection) {
        return connection.repoPath() + "/issues/" + pullRequestNumber;
    }

    private void snapshot(final GHPullRequest pr) throws IOException {
        this.id = pr.getId();
        this.pullRequestNumber = pr.getNumber();
        this.diffUrl = url(pr.getDiffUrl());
        this.url = url(pr.getHtmlUrl());
        this.patchUrl = url(pr.getPatchUrl());
        this.state = lower(pr.getState());
        this.issueUrl = url(pr.getIssueUrl());
        this.title = pr.getTitle();
        this.body = pr.getBody();
        this.locked = pr.isLocked();
        this.draft = pr.isDraft();
        this.milestone = pr.getMilestone() == null ? null : new MilestoneGroovyObject(pr.getMilestone());
        this.headSha = pr.getHead() == null ? null : pr.getHead().getSha();
        this.headRef = pr.getHead() == null ? null : pr.getHead().getRef();
        this.baseSha = pr.getBase() == null ? null : pr.getBase().getSha();
        this.baseRef = pr.getBase() == null ? null : pr.getBase().getRef();
        this.updatedAt = pr.getUpdatedAt();
        this.createdAt = pr.getCreatedAt();
        this.createdBy = login(pr.getUser());
        this.closedAt = pr.getClosedAt();
        this.closedBy = login(pr.getClosedBy());
        this.mergedAt = pr.getMergedAt();
        this.mergedBy = login(pr.getMergedBy());
        this.commitCount = pr.getCommits();
        this.commentCount = pr.getCommentsCount();
        this.deletions = pr.getDeletions();
        this.mergeCommitSha = pr.getMergeCommitSha();
        this.mergeableState = pr.getMergeableState();
        this.maintainerCanModify = pr.canMaintainerModify();
        this.additions = pr.getAdditions();
        this.changedFiles = pr.getChangedFiles();
        this.mergeable = Boolean.TRUE.equals(pr.getMergeable());
        this.merged = pr.isMerged();
        this.assignees = new ArrayList<>(pr.getAssignees().stream().map(GHUser::getLogin).collect(toList()));
    }

    private interface Action<T> {
        T run(GitHubConnection connection) throws IOException;
    }

    private <T> T call(final Action<T> action) {
        try (GitHubConnection connection = connect()) {
            return action.run(connection);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Runs a change, then refreshes the snapshot. */
    private void change(final Action<?> action) {
        call(connection -> {
            action.run(connection);
            snapshot(fetch(connection));
            return null;
        });
    }

    private void editPullRequest(final String field, final Object value) {
        change(connection -> connection.rest().patch(pullPath(connection),
                Collections.singletonMap(field, value), GitHubRest.ACCEPT_JSON));
    }

    @Whitelisted
    public long getId() {
        return id;
    }

    @Whitelisted
    public int getNumber() {
        return pullRequestNumber;
    }

    @Whitelisted
    public String getDiffUrl() {
        return diffUrl;
    }

    @Whitelisted
    public String getUrl() {
        return url;
    }

    @Whitelisted
    public String getPatchUrl() {
        return patchUrl;
    }

    @Whitelisted
    public String getState() {
        return state;
    }

    @Whitelisted
    public String getIssueUrl() {
        return issueUrl;
    }

    @Whitelisted
    public String getTitle() {
        return title;
    }

    @Whitelisted
    public String getBody() {
        return body;
    }

    @Whitelisted
    public boolean isLocked() {
        return locked;
    }

    @Whitelisted
    public boolean isDraft() {
        return draft;
    }

    @Whitelisted
    public MilestoneGroovyObject getMilestone() {
        return milestone;
    }

    @Whitelisted
    public String getHead() {
        return headSha;
    }

    @Whitelisted
    public String getHeadRef() {
        return headRef;
    }

    @Whitelisted
    public String getBaseSha() {
        return baseSha;
    }

    @Whitelisted
    public String getBase() {
        return baseRef;
    }

    @Whitelisted
    public Date getUpdatedAt() {
        return copy(updatedAt);
    }

    @Whitelisted
    public Date getCreatedAt() {
        return copy(createdAt);
    }

    @Whitelisted
    public String getCreatedBy() {
        return createdBy;
    }

    /** The egit version returned the creation date here. */
    @Whitelisted
    public Date getClosedAt() {
        return copy(closedAt);
    }

    /** Always null: GitHub's pull request API has no closed_by (it never did). */
    @Whitelisted
    public String getClosedBy() {
        return closedBy;
    }

    @Whitelisted
    public Date getMergedAt() {
        return copy(mergedAt);
    }

    @Whitelisted
    public String getMergedBy() {
        return mergedBy;
    }

    @Whitelisted
    public int getCommitCount() {
        return commitCount;
    }

    @Whitelisted
    public int getCommentCount() {
        return commentCount;
    }

    @Whitelisted
    public int getDeletions() {
        return deletions;
    }

    @Whitelisted
    public String getMergeCommitSha() {
        return mergeCommitSha;
    }

    @Whitelisted
    public String getMergeableState() {
        return mergeableState;
    }

    @Whitelisted
    public boolean isMaintainerCanModify() {
        return maintainerCanModify;
    }

    @Whitelisted
    public int getAdditions() {
        return additions;
    }

    @Whitelisted
    public int getChangedFiles() {
        return changedFiles;
    }

    @Whitelisted
    public boolean isMergeable() {
        return mergeable;
    }

    @Whitelisted
    public boolean isMerged() {
        return merged;
    }

    @Whitelisted
    public Iterable<String> getRequestedReviewers() {
        return call(connection -> fetch(connection).getRequestedReviewers()
                .stream()
                .map(GHUser::getLogin)
                .collect(toList()));
    }

    @Whitelisted
    public Iterable<String> getRequestedTeamReviewers() {
        return call(connection -> fetch(connection).getRequestedTeams()
                .stream()
                .map(GHTeam::getName)
                .collect(toList()));
    }

    @Whitelisted
    public Iterable<ReviewGroovyObject> getReviews() {
        return call(connection -> fetch(connection).listReviews().toList()
                .stream()
                .map(ReviewGroovyObject::new)
                .collect(toList()));
    }

    @Whitelisted
    public Iterable<CommitStatusGroovyObject> getStatuses() {
        return call(connection -> connection.repository().listCommitStatuses(headSha).toList()
                .stream()
                .map(CommitStatusGroovyObject::new)
                .collect(toList()));
    }

    @Whitelisted
    public Iterable<String> getLabels() {
        return call(connection -> connection.repository().getIssue(pullRequestNumber).getLabels()
                .stream()
                .map(GHLabel::getName)
                .collect(toList()));
    }

    @Whitelisted
    public Iterable<String> getAssignees() {
        return new ArrayList<>(assignees);
    }

    @Whitelisted
    public Iterable<CommitGroovyObject> getCommits() {
        return call(connection -> connection.rest().getAll(pullPath(connection) + "/commits", GitHubRest.ACCEPT_JSON)
                .stream()
                .map(c -> new CommitGroovyObject(jobId, c, credentials))
                .collect(toList()));
    }

    @Whitelisted
    public Iterable<IssueCommentGroovyObject> getComments() {
        return call(connection -> connection.rest()
                .getAll(issuePath(connection) + "/comments", GitHubRest.ACCEPT_FULL_JSON)
                .stream()
                .map(c -> new IssueCommentGroovyObject(jobId, c, credentials))
                .collect(toList()));
    }

    @Whitelisted
    public Iterable<ReviewCommentGroovyObject> getReviewComments() {
        return call(connection -> connection.rest().getAll(pullPath(connection) + "/comments", GitHubRest.ACCEPT_JSON)
                .stream()
                .map(c -> new ReviewCommentGroovyObject(jobId, ReviewCommentGroovyObject.Kind.PULL_REQUEST, c,
                        credentials))
                .collect(toList()));
    }

    @Whitelisted
    public Iterable<CommitFileGroovyObject> getFiles() {
        return call(connection -> fetch(connection).listFiles().toList()
                .stream()
                .map(CommitFileGroovyObject::new)
                .collect(toList()));
    }

    @Whitelisted
    public void setMilestone(final int milestoneNumber) {
        setMilestoneNumber(milestoneNumber);
    }

    @Whitelisted
    public void setMilestone(final MilestoneGroovyObject milestone) {
        setMilestoneNumber(milestone == null ? null : milestone.getNumber());
    }

    private void setMilestoneNumber(@CheckForNull final Integer milestoneNumber) {
        // hub4j needs a GHMilestone (another call) and the REST API takes the number
        change(connection -> connection.rest().patch(issuePath(connection),
                Collections.singletonMap("milestone", milestoneNumber), GitHubRest.ACCEPT_JSON));
    }

    @Whitelisted
    public void setLocked(final boolean locked) {
        change(connection -> {
            GHPullRequest pr = fetch(connection);
            if (locked) {
                pr.lock();
            } else {
                pr.unlock();
            }
            return null;
        });
    }

    @Whitelisted
    public void setTitle(final String title) {
        Objects.requireNonNull(title, "title cannot be null");
        change(connection -> {
            fetch(connection).setTitle(title);
            return null;
        });
    }

    @Whitelisted
    public void setBody(final String body) {
        Objects.requireNonNull(body, "body cannot be null");
        change(connection -> {
            fetch(connection).setBody(body);
            return null;
        });
    }

    @Whitelisted
    public void setState(final String state) {
        Objects.requireNonNull(state, "state cannot be null");
        GHIssueState target;
        try {
            target = GHIssueState.valueOf(state.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("state must be open or closed; was " + state, e);
        }
        change(connection -> {
            GHPullRequest pr = fetch(connection);
            if (target == GHIssueState.CLOSED) {
                pr.close();
            } else if (target == GHIssueState.OPEN) {
                pr.reopen();
            } else {
                throw new IllegalArgumentException("state must be open or closed; was " + state);
            }
            return null;
        });
    }

    @Whitelisted
    public void setBase(final String newBase) {
        Objects.requireNonNull(newBase, "base cannot be null");
        change(connection -> fetch(connection).setBaseBranch(newBase));
    }

    /** hub4j can only set maintainer_can_modify when creating a pull request. */
    @Whitelisted
    public void setMaintainerCanModify(final boolean value) {
        editPullRequest("maintainer_can_modify", value);
    }

    @Whitelisted
    public void setLabels(final List<String> labels) {
        List<String> names = Optional.ofNullable(labels).orElseGet(Collections::emptyList);
        call(connection -> {
            fetch(connection).setLabels(names.toArray(new String[0]));
            return null;
        });
    }

    /** Review requests go through the REST API: hub4j needs a GHUser or GHTeam per name and can't remove one. */
    private void reviewRequests(final String method,
                                @CheckForNull final List<String> reviewers,
                                @CheckForNull final List<String> teamReviewers) {
        Map<String, Object> params = new HashMap<>();
        if (reviewers != null) {
            params.put("reviewers", reviewers);
        }
        if (teamReviewers != null) {
            params.put("team_reviewers", teamReviewers);
        }
        call(connection -> connection.rest().send(method, pullPath(connection) + "/requested_reviewers", params,
                GitHubRest.ACCEPT_JSON));
    }

    @Whitelisted
    public void createReviewRequest(final String reviewer) {
        Objects.requireNonNull(reviewer, "reviewer cannot be null");
        createReviewRequests(Collections.singletonList(reviewer));
    }

    @Whitelisted
    public void createReviewRequests(final List<String> reviewers) {
        Objects.requireNonNull(reviewers, "reviewers cannot be null");
        reviewRequests("POST", reviewers, null);
    }

    @Whitelisted
    public void deleteReviewRequest(final String reviewer) {
        Objects.requireNonNull(reviewer, "reviewer cannot be null");
        deleteReviewRequests(Collections.singletonList(reviewer));
    }

    @Whitelisted
    public void deleteReviewRequests(final List<String> reviewers) {
        Objects.requireNonNull(reviewers, "reviewers cannot be null");
        reviewRequests("DELETE", reviewers, null);
    }

    @Whitelisted
    public void createTeamReviewRequest(final String team) {
        Objects.requireNonNull(team, "team cannot be null");
        createTeamReviewRequests(Collections.singletonList(team));
    }

    @Whitelisted
    public void createTeamReviewRequests(final List<String> teams) {
        Objects.requireNonNull(teams, "teams cannot be null");
        reviewRequests("POST", null, teams);
    }

    @Whitelisted
    public void deleteTeamReviewRequest(final String team) {
        Objects.requireNonNull(team, "team cannot be null");
        deleteTeamReviewRequests(Collections.singletonList(team));
    }

    @Whitelisted
    public void deleteTeamReviewRequests(final List<String> teams) {
        Objects.requireNonNull(teams, "teams cannot be null");
        reviewRequests("DELETE", null, teams);
    }

    @Whitelisted
    public void addLabel(final String label) {
        addLabels(Collections.singletonList(label));
    }

    @Whitelisted
    public void addLabels(final List<String> labels) {
        Objects.requireNonNull(labels, "labels is a required argument");
        call(connection -> fetch(connection).addLabels(labels.toArray(new String[0])));
    }

    @Whitelisted
    public void removeLabel(final String label) {
        Objects.requireNonNull(label, "label is a required argument");
        call(connection -> fetch(connection).removeLabel(label));
    }

    /** Assignees go through the REST API: hub4j needs a GHUser (another call) per login. */
    private void assignees(final String method, final String path, final List<String> assignees) {
        change(connection -> connection.rest().send(method, issuePath(connection) + path,
                Collections.singletonMap("assignees", assignees), GitHubRest.ACCEPT_JSON));
    }

    @Whitelisted
    public void addAssignees(final List<String> assignees) {
        Objects.requireNonNull(assignees, "assignees is a required argument");
        assignees("POST", "/assignees", assignees);
    }

    /** Replaces the assignees (PATCH the issue; the egit version used a PUT that GitHub doesn't document). */
    @Whitelisted
    public void setAssignees(final List<String> assignees) {
        Objects.requireNonNull(assignees, "assignees is a required argument");
        assignees("PATCH", "", assignees);
    }

    @Whitelisted
    public void removeAssignees(final List<String> assignees) {
        Objects.requireNonNull(assignees, "assignees is a required argument");
        assignees("DELETE", "/assignees", assignees);
    }

    @Whitelisted
    public void review(final String event) {
        review(null, event, null);
    }

    @Whitelisted
    public void review(final String event, final String body) {
        review(null, event, body);
    }

    @Whitelisted
    public void review(final Map<String, Object> params) {
        review(params.get("commitId") != null ? params.get("commitId").toString() : null,
               params.get("event") != null ? params.get("event").toString() : null,
               params.get("body") != null ? params.get("body").toString() : null);
    }

    /**
     * @param event APPROVE, REQUEST_CHANGES or COMMENT; null leaves the review PENDING
     */
    @Whitelisted
    public void review(final String commitId, final String event, final String body) {
        if (event != null && (event.equals("REQUEST_CHANGES") || event.equals("COMMENT"))) {
            Objects.requireNonNull(body, "body is a required argument when event equals REQUEST_CHANGES or COMMENT");
        }
        GHPullRequestReviewEvent reviewEvent = event == null ? null : GHPullRequestReviewEvent.valueOf(event);
        call(connection -> {
            GHPullRequestReviewBuilder review = fetch(connection).createReview();
            if (commitId != null) {
                review.commitId(commitId);
            }
            if (body != null) {
                review.body(body);
            }
            if (reviewEvent != null) {
                review.event(reviewEvent);
            }
            return review.create();
        });
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
        return call(connection ->
                CommitGroovyObject.createStatus(connection, headSha, status, context, description, targetUrl));
    }

    @Whitelisted
    public ReviewCommentGroovyObject reviewComment(final String commitId,
                                                   final String path,
                                                   final int line,
                                                   final String body) {
        Objects.requireNonNull(commitId, "commitId is a required argument");
        Objects.requireNonNull(path, "path is a required argument");
        Objects.requireNonNull(body, "body is a required argument");
        Map<String, Object> params = new HashMap<>();
        params.put("commit_id", commitId);
        params.put("path", path);
        params.put("line", line);
        params.put("side", "RIGHT");
        params.put("body", body);
        return call(connection -> new ReviewCommentGroovyObject(jobId, ReviewCommentGroovyObject.Kind.PULL_REQUEST,
                connection.rest().post(pullPath(connection) + "/comments", params, GitHubRest.ACCEPT_JSON),
                credentials));
    }

    @Whitelisted
    public ReviewCommentGroovyObject replyToReviewComment(final long commentId, final String body) {
        Objects.requireNonNull(body, "body is a required argument");
        return call(connection -> new ReviewCommentGroovyObject(jobId, ReviewCommentGroovyObject.Kind.PULL_REQUEST,
                ReviewCommentGroovyObject.reply(connection, pullRequestNumber, commentId, body), credentials));
    }

    @Whitelisted
    public void deleteReviewComment(final long commentId) {
        call(connection -> {
            ReviewCommentGroovyObject.delete(connection, ReviewCommentGroovyObject.Kind.PULL_REQUEST, commentId);
            return null;
        });
    }

    @Whitelisted
    public ReviewCommentGroovyObject editReviewComment(final long commentId, final String body) {
        Objects.requireNonNull(body, "body is a required argument");
        return call(connection -> new ReviewCommentGroovyObject(jobId, ReviewCommentGroovyObject.Kind.PULL_REQUEST,
                ReviewCommentGroovyObject.edit(connection, ReviewCommentGroovyObject.Kind.PULL_REQUEST, commentId,
                        body),
                credentials));
    }

    @Whitelisted
    public IssueCommentGroovyObject comment(final String body) {
        Objects.requireNonNull(body, "body is a required argument");
        return call(connection -> new IssueCommentGroovyObject(jobId,
                connection.rest().post(issuePath(connection) + "/comments",
                        Collections.singletonMap("body", body), GitHubRest.ACCEPT_FULL_JSON),
                credentials));
    }

    @Whitelisted
    public IssueCommentGroovyObject editComment(final long commentId, final String body) {
        Objects.requireNonNull(body, "body is a required argument");
        return call(connection -> new IssueCommentGroovyObject(jobId,
                IssueCommentGroovyObject.edit(connection, commentId, body), credentials));
    }

    @Whitelisted
    public void deleteComment(final long commentId) {
        call(connection -> {
            IssueCommentGroovyObject.delete(connection, commentId);
            return null;
        });
    }

    @Whitelisted
    public String merge(final Map<String, Object> params) {
        return merge(Optional.ofNullable(params.get("commitTitle")).map(Object::toString).orElse(null),
                     Optional.ofNullable(params.get("commitMessage")).map(Object::toString).orElse(null),
                     Optional.ofNullable(params.get("sha")).map(Object::toString).orElse(null),
                     Optional.ofNullable(params.get("mergeMethod")).map(Object::toString).orElse(null));
    }

    @Whitelisted
    public String merge(final String commitMessage) {
        return merge(null, commitMessage, null, null);
    }

    /**
     * Merges through the REST API: hub4j's merge has no commit title and doesn't return the merge commit.
     *
     * @return the merge commit's SHA
     */
    @Whitelisted
    public String merge(final String commitTitle,
                        final String commitMessage,
                        final String sha,
                        final String mergeMethod) {
        Map<String, String> params = new HashMap<>();
        if (commitTitle != null) {
            params.put("commit_title", commitTitle);
        }
        if (commitMessage != null) {
            params.put("commit_message", commitMessage);
        }
        if (sha != null) {
            params.put("sha", sha);
        }
        if (mergeMethod != null) {
            params.put("merge_method", mergeMethod);
        }
        JsonNode status = call(connection -> connection.rest().put(pullPath(connection) + "/merge", params));
        if (status != null && status.path("merged").asBoolean(false)) {
            return GitHubRest.text(status, "sha");
        }
        throw new RuntimeException(status == null ? "Pull request was not merged" : GitHubRest.text(status, "message"));
    }

    @Whitelisted
    public void refresh() {
        call(connection -> {
            snapshot(fetch(connection));
            return null;
        });
    }

    /**
     * Uses these credentials instead of the job's scan credentials for the rest of this object's life (they are not
     * persisted, so a resumed build goes back to the scan credentials).
     */
    @Whitelisted
    public void setCredentials(final String userName, final String password) {
        try {
            this.credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
                    "pipeline-github-" + UUID.randomUUID(), null, userName, password);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Invalid credentials: " + e.getMessage(), e);
        }
    }

    /** Deletes the head branch (in the base repository, as before). */
    @Whitelisted
    public void deleteBranch() {
        call(connection -> connection.rest().delete(connection.repoPath() + "/git/refs/heads/" + headRef, null));
    }
}
