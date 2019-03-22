package com.atlassian.jira.plugin.ext.subversion.projecttabpanels;

import com.atlassian.jira.plugin.ext.subversion.MultipleSubversionRepositoryManager;
import com.atlassian.jira.plugin.ext.subversion.issuetabpanels.changes.SubversionRevisionAction;
import com.atlassian.jira.plugin.projectpanel.ProjectTabPanelModuleDescriptor;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import org.ofbiz.core.util.UtilMisc;
import org.tmatesoft.svn.core.SVNLogEntry;

import java.util.Map;

/**
 * One item in the 'Subversion Commits' project tab.
 * <p/>
 * This class extends {@link SubversionRevisionAction} (basically, there is no issue to group by here,
 * and we need to use a ProjectTabPanelModuleDescriptor in stead of an IssueTabPanelModuleDescriptor)
 */
public class SubversionProjectRevisionAction extends SubversionRevisionAction {
    protected final ProjectTabPanelModuleDescriptor projectDescriptor;

    public SubversionProjectRevisionAction(SVNLogEntry logEntry,
                                           MultipleSubversionRepositoryManager multipleSubversionRepositoryManager,
                                           ProjectTabPanelModuleDescriptor descriptor, long repoId) {
        super(logEntry, multipleSubversionRepositoryManager, null, repoId);
        this.projectDescriptor = descriptor;
    }

    public String getHtml(JiraWebActionSupport webAction) {
        Map params = UtilMisc.toMap("webAction", webAction, "action", this);
        return descriptor.getHtml("view", params);
    }
}
