package com.atlassian.jira.plugin.ext.subversion.action;

import com.atlassian.jira.plugin.ext.subversion.MultipleSubversionRepositoryManager;
import com.atlassian.jira.plugin.ext.subversion.SubversionManager;

public class DeleteSubversionRepositoryAction extends SubversionActionSupport {
    private long repoId;
    private SubversionManager subversionManager;

    public DeleteSubversionRepositoryAction(MultipleSubversionRepositoryManager manager) {
        super(manager);
    }

    public String getRepoId() {
        return Long.toString(repoId);
    }

    public void setRepoId(String repoId) {
        this.repoId = Long.parseLong(repoId);
    }

    public String doDefault() {
        if (!hasPermissions()) {
            return PERMISSION_VIOLATION_RESULT;
        }

        subversionManager = getMultipleRepoManager().getRepository(repoId);
        return INPUT;
    }

    public String doExecute() {
        if (!hasPermissions()) {
            return PERMISSION_VIOLATION_RESULT;
        }

        getMultipleRepoManager().removeRepository(repoId);
        return getRedirect("ViewSubversionRepositories.jspa");
    }

    public SubversionManager getSubversionManager() {
        return subversionManager;
    }
}
