package org.jenkinsci.plugins.pipeline.github;

import groovy.lang.GroovyObjectSupport;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import com.fasterxml.jackson.databind.JsonNode;
import org.kohsuke.github.GHPullRequestFileDetail;

import java.io.Serializable;
import java.util.Objects;

import static org.jenkinsci.plugins.pipeline.github.Snapshots.url;

/**
 * Groovy wrapper over a file changed by a pull request or a commit.
 *
 * @author Aaron Whiteside
 */
public class CommitFileGroovyObject extends GroovyObjectSupport implements Serializable {
    private static final long serialVersionUID = 2L;

    private final int additions;
    private final int changes;
    private final int deletions;
    private final String blobUrl;
    private final String filename;
    private final String patch;
    private final String rawUrl;
    private final String sha;
    private final String status;

    CommitFileGroovyObject(final GHPullRequestFileDetail file) {
        Objects.requireNonNull(file, "file cannot be null");
        this.additions = file.getAdditions();
        this.changes = file.getChanges();
        this.deletions = file.getDeletions();
        this.blobUrl = url(file.getBlobUrl());
        this.filename = file.getFilename();
        this.patch = file.getPatch();
        this.rawUrl = url(file.getRawUrl());
        this.sha = file.getSha();
        this.status = file.getStatus();
    }

    /** From a file in a {@code GET /repos/{owner}/{repo}/commits/{ref}} response. */
    CommitFileGroovyObject(final JsonNode file) {
        Objects.requireNonNull(file, "file cannot be null");
        this.additions = (int) GitHubRest.number(file, "additions");
        this.changes = (int) GitHubRest.number(file, "changes");
        this.deletions = (int) GitHubRest.number(file, "deletions");
        this.blobUrl = GitHubRest.text(file, "blob_url");
        this.filename = GitHubRest.text(file, "filename");
        this.patch = GitHubRest.text(file, "patch");
        this.rawUrl = GitHubRest.text(file, "raw_url");
        this.sha = GitHubRest.text(file, "sha");
        this.status = GitHubRest.text(file, "status");
    }

    @Whitelisted
    public int getAdditions() {
        return additions;
    }

    @Whitelisted
    public int getChanges() {
        return changes;
    }

    @Whitelisted
    public int getDeletions() {
        return deletions;
    }

    @Whitelisted
    public String getBlobUrl() {
        return blobUrl;
    }

    @Whitelisted
    public String getFilename() {
        return filename;
    }

    @Whitelisted
    public String getPatch() {
        return patch;
    }

    @Whitelisted
    public String getRawUrl() {
        return rawUrl;
    }

    @Whitelisted
    public String getSha() {
        return sha;
    }

    @Whitelisted
    public String getStatus() {
        return status;
    }
}
