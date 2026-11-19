package org.jenkinsci.plugins.pipeline.github;

import groovy.lang.GroovyObjectSupport;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.github.GHCommitStatus;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

import static org.jenkinsci.plugins.pipeline.github.Snapshots.copy;
import static org.jenkinsci.plugins.pipeline.github.Snapshots.login;
import static org.jenkinsci.plugins.pipeline.github.Snapshots.lower;
import static org.jenkinsci.plugins.pipeline.github.Snapshots.unchecked;
import static org.jenkinsci.plugins.pipeline.github.Snapshots.url;

/**
 * Groovy wrapper over a commit status: a snapshot of a {@link GHCommitStatus}.
 *
 * @author Aaron Whiteside
 */
public class CommitStatusGroovyObject extends GroovyObjectSupport implements Serializable {
    private static final long serialVersionUID = 2L;

    private final String creator;
    private final Date createdAt;
    private final Date updatedAt;
    private final long id;
    private final String context;
    private final String description;
    private final String state;
    private final String targetUrl;
    private final String url;

    CommitStatusGroovyObject(final GHCommitStatus commitStatus) {
        Objects.requireNonNull(commitStatus, "commitStatus cannot be null");
        this.creator = unchecked(() -> login(commitStatus.getCreator()));
        this.createdAt = unchecked(commitStatus::getCreatedAt);
        this.updatedAt = unchecked(commitStatus::getUpdatedAt);
        this.id = commitStatus.getId();
        this.context = commitStatus.getContext();
        this.description = commitStatus.getDescription();
        this.state = lower(commitStatus.getState());
        this.targetUrl = commitStatus.getTargetUrl();
        this.url = url(commitStatus.getUrl());
    }

    @Whitelisted
    public String getCreator() {
        return creator;
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
    public long getId() {
        return id;
    }

    @Whitelisted
    public String getContext() {
        return context;
    }

    @Whitelisted
    public String getDescription() {
        return description;
    }

    @Whitelisted
    public String getState() {
        return state;
    }

    @Whitelisted
    public String getTargetUrl() {
        return targetUrl;
    }

    @Whitelisted
    public String getUrl() {
        return url;
    }
}
