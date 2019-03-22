package com.atlassian.jira.plugin.ext.subversion.action;

import com.atlassian.jira.plugin.ext.subversion.MultipleSubversionRepositoryManager;
import com.atlassian.jira.plugin.ext.subversion.SubversionManager;


public class UpdateSubversionRepositoryAction extends AddSubversionRepositoryAction {
    private long repoId = -1;

    public UpdateSubversionRepositoryAction(MultipleSubversionRepositoryManager multipleRepoManager) {
        super(multipleRepoManager);
    }

    public String doDefault() {
        if (ERROR.equals(super.doDefault()))
            return ERROR;

        if (!hasPermissions()) {
            return PERMISSION_VIOLATION_RESULT;
        }


        if (repoId == -1) {
            addErrorMessage(getText("subversion.repository.id.missing"));
            return ERROR;
        }

        // Retrieve the cvs repository
        final SubversionManager repository = getMultipleRepoManager().getRepository(repoId);
        if (repository == null) {
            addErrorMessage(getText("subversion.repository.does.not.exist", Long.toString(repoId)));
            return ERROR;
        }

        this.setDisplayName(repository.getDisplayName());
        this.setRoot(repository.getRoot());
        if (repository.getViewLinkFormat() != null) {
            this.setWebLinkType(repository.getViewLinkFormat().getType());
            this.setChangesetFormat(repository.getViewLinkFormat().getChangesetFormat());
            this.setViewFormat(repository.getViewLinkFormat().getViewFormat());
            this.setFileAddedFormat(repository.getViewLinkFormat().getFileAddedFormat());
            this.setFileDeletedFormat(repository.getViewLinkFormat().getFileDeletedFormat());
            this.setFileModifiedFormat(repository.getViewLinkFormat().getFileModifiedFormat());
            this.setFileReplacedFormat(repository.getViewLinkFormat().getFileReplacedFormat());
        }
        this.setUsername(repository.getUsername());
        this.setPassword(repository.getPassword());
        this.setPrivateKeyFile(repository.getPrivateKeyFile());
        this.setRevisionCacheSize(new Integer(repository.getRevisioningCacheSize()));
        this.setRevisionIndexing(true);

        return INPUT;
    }

    public String doExecute() {
        if (!hasPermissions()) {
            addErrorMessage(getText("subversion.admin.privilege.required"));
            return ERROR;
        }

        if (repoId == -1) {
            return getRedirect("ViewSubversionRepositories.jspa");
        }

        SubversionManager subversionManager = getMultipleRepoManager().updateRepository(repoId, this);
        if (!subversionManager.isActive()) {
            repoId = subversionManager.getId();
            addErrorMessage(subversionManager.getInactiveMessage());
            addErrorMessage(getText("admin.errors.occured.when.updating"));
            return ERROR;
        }
        return getRedirect("ViewSubversionRepositories.jspa");
    }

    public long getRepoId() {
        return repoId;
    }

    public void setRepoId(long repoId) {
        this.repoId = repoId;
    }

}
