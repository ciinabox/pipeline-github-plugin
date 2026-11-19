package org.jenkinsci.plugins.pipeline.github;

import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.lang.GroovyObjectSupport;
import hudson.model.Job;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext;
import org.kohsuke.github.GHRepository;

import java.io.Serializable;
import java.net.URL;

/**
 * Groovy object that represents a GitHub Repository.
 *
 * <p>Holds a snapshot of the repository taken when the object was created.
 */
@PersistIn(PersistenceContext.NONE)
public class RepositoryGroovyObject extends GroovyObjectSupport implements Serializable {

    private static final long serialVersionUID = 2L;

    private final String name;
    private final String owner;
    private final String defaultBranch;
    private final String description;
    private final String htmlUrl;
    private final String cloneUrl;
    private final String sshUrl;
    private final String gitUrl;
    private final boolean isPrivate;
    private final boolean fork;
    private final long id;

    RepositoryGroovyObject(@NonNull final Job job) throws Exception {
        try (GitHubConnection connection = GitHubConnection.open(job)) {
            GHRepository repository = connection.repository();
            this.name = repository.getName();
            this.owner = repository.getOwnerName();
            this.defaultBranch = repository.getDefaultBranch();
            this.description = repository.getDescription();
            this.htmlUrl = toString(repository.getHtmlUrl());
            this.cloneUrl = repository.getHttpTransportUrl();
            this.sshUrl = repository.getSshUrl();
            this.gitUrl = repository.getGitTransportUrl();
            this.isPrivate = repository.isPrivate();
            this.fork = repository.isFork();
            this.id = repository.getId();
        }
    }

    private static String toString(final URL url) {
        return url == null ? null : url.toString();
    }

    @Whitelisted
    public String getName() {
        return name;
    }

    @Whitelisted
    public String getOwner() {
        return owner;
    }

    @Whitelisted
    public String getDefaultBranch() {
        return defaultBranch;
    }

    @Whitelisted
    public String getDescription() {
        return description;
    }

    @Whitelisted
    public String getHtmlUrl() {
        return htmlUrl;
    }

    @Whitelisted
    public String getCloneUrl() {
        return cloneUrl;
    }

    @Whitelisted
    public String getSshUrl() {
        return sshUrl;
    }

    @Whitelisted
    public String getGitUrl() {
        return gitUrl;
    }

    @Whitelisted
    public boolean isPrivate() {
        return isPrivate;
    }

    @Whitelisted
    public boolean isFork() {
        return fork;
    }

    @Whitelisted
    public long getId() {
        return id;
    }
}
