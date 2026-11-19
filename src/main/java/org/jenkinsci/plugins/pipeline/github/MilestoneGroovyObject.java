package org.jenkinsci.plugins.pipeline.github;

import groovy.lang.GroovyObjectSupport;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.github.GHMilestone;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

import static org.jenkinsci.plugins.pipeline.github.Snapshots.copy;
import static org.jenkinsci.plugins.pipeline.github.Snapshots.login;
import static org.jenkinsci.plugins.pipeline.github.Snapshots.lower;
import static org.jenkinsci.plugins.pipeline.github.Snapshots.unchecked;
import static org.jenkinsci.plugins.pipeline.github.Snapshots.url;

/**
 * Groovy wrapper over a milestone.
 *
 * @author Aaron Whiteside
 */
public class MilestoneGroovyObject extends GroovyObjectSupport implements Serializable {
    private static final long serialVersionUID = 2L;

    private final Date createdAt;
    private final Date dueOn;
    private final int closedIssues;
    private final int number;
    private final int openIssues;
    private final String description;
    private final String state;
    private final String title;
    private final String url;
    private final String creator;
    private final Date updatedAt;
    private final Date closedAt;

    MilestoneGroovyObject(final GHMilestone milestone) {
        Objects.requireNonNull(milestone, "milestone cannot be null");
        this.createdAt = unchecked(milestone::getCreatedAt);
        this.dueOn = milestone.getDueOn();
        this.closedIssues = milestone.getClosedIssues();
        this.number = milestone.getNumber();
        this.openIssues = milestone.getOpenIssues();
        this.description = milestone.getDescription();
        this.state = lower(milestone.getState());
        this.title = milestone.getTitle();
        this.url = url(milestone.getUrl());
        this.creator = unchecked(() -> login(milestone.getCreator()));
        this.updatedAt = unchecked(milestone::getUpdatedAt);
        this.closedAt = unchecked(milestone::getClosedAt);
    }

    @Whitelisted
    public Date getCreatedAt() {
        return copy(createdAt);
    }

    @Whitelisted
    public Date getDueOn() {
        return copy(dueOn);
    }

    @Whitelisted
    public int getClosedIssues() {
        return closedIssues;
    }

    @Whitelisted
    public int getNumber() {
        return number;
    }

    @Whitelisted
    public int getOpenIssues() {
        return openIssues;
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
    public String getTitle() {
        return title;
    }

    @Whitelisted
    public String getUrl() {
        return url;
    }

    @Whitelisted
    public String getCreator() {
        return creator;
    }

    @Whitelisted
    public Date getUpdatedAt() {
        return copy(updatedAt);
    }

    @Whitelisted
    public Date getClosedAt() {
        return copy(closedAt);
    }
}
