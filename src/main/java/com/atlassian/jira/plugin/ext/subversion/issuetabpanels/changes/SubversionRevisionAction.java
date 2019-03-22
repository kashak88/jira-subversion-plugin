/*
 * Created by IntelliJ IDEA.
 * User: Mike
 * Date: Sep 16, 2004
 * Time: 2:00:52 PM
 */
package com.atlassian.jira.plugin.ext.subversion.issuetabpanels.changes;

import com.atlassian.jira.plugin.ext.subversion.MultipleSubversionRepositoryManager;
import com.atlassian.jira.plugin.ext.subversion.SubversionConstants;
import com.atlassian.jira.plugin.ext.subversion.linkrenderer.SubversionLinkRenderer;
import com.atlassian.jira.plugin.issuetabpanel.AbstractIssueAction;
import com.atlassian.jira.plugin.issuetabpanel.IssueTabPanelModuleDescriptor;
import com.atlassian.jira.util.JiraKeyUtils;
import org.apache.commons.lang.StringUtils;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One item in the 'Subversion Commits' tab.
 */
public class SubversionRevisionAction extends AbstractIssueAction {


    private final SVNLogEntry revision;
    private final long repoId;
    protected final IssueTabPanelModuleDescriptor descriptor;
    protected MultipleSubversionRepositoryManager multipleSubversionRepositoryManager;

    public SubversionRevisionAction(SVNLogEntry logEntry, MultipleSubversionRepositoryManager multipleSubversionRepositoryManager, IssueTabPanelModuleDescriptor descriptor, long repoId) {
        super(descriptor);
        this.multipleSubversionRepositoryManager = multipleSubversionRepositoryManager;
        this.descriptor = descriptor;
        /* SVN-93 */
        this.revision = new SVNLogEntry(
                logEntry.getChangedPaths(),
                logEntry.getRevision(),
                logEntry.getAuthor(),
                logEntry.getDate(),
                rewriteLogMessage(logEntry.getMessage()));
        this.repoId = repoId;
    }

    protected void populateVelocityParams(Map params) {
        params.put("stringUtils", new StringUtils());
        params.put("svn", this);
    }

    public SubversionLinkRenderer getLinkRenderer() {
        return multipleSubversionRepositoryManager.getRepository(repoId).getLinkRenderer();
    }

    public String getRepositoryDisplayName() {
        return multipleSubversionRepositoryManager.getRepository(repoId).getDisplayName();
    }

    public Date getTimePerformed() {
        if (revision.getDate() == null) {
            throw new UnsupportedOperationException("no revision date for this log entry");
        }
        return revision.getDate();
    }

    public long getRepoId() {
        return repoId;
    }

    public String getUsername() {
        return revision.getAuthor();
    }

    public SVNLogEntry getRevision() {
        return revision;
    }

    public boolean isAdded(SVNLogEntryPath logEntryPath) {
        return SubversionConstants.ADDED == logEntryPath.getType();
    }

    public boolean isModified(SVNLogEntryPath logEntryPath) {
        return SubversionConstants.MODIFICATION == logEntryPath.getType();
    }

    public boolean isReplaced(SVNLogEntryPath logEntryPath) {
        return SubversionConstants.REPLACED == logEntryPath.getType();
    }

    public boolean isDeleted(SVNLogEntryPath logEntryPath) {
        return SubversionConstants.DELETED == logEntryPath.getType();
    }

    /**
     * Converts all lower case JIRA issue keys to upper case so that they can be
     * correctly rendered in the Velocity macro, makelinkedhtml.
     *
     * @param logMessageToBeRewritten The SVN log message to be rewritten.
     * @return The rewritten SVN log message.
     * @see <a href="http://jira.atlassian.com/browse/SVN-93">SVN-93</a>
     */
    protected String rewriteLogMessage(final String logMessageToBeRewritten) {
        String logMessage = logMessageToBeRewritten;
        final String logMessageUpperCase = StringUtils.upperCase(logMessage);
        final Set<String> issueKeys = new HashSet<String>(getIssueKeysFromCommitMessage(logMessageUpperCase));

        for (String issueKey : issueKeys)
            logMessage = logMessage.replaceAll("(?ium)" + issueKey, issueKey);

        return logMessage;
    }

    List<String> getIssueKeysFromCommitMessage(String logMessageUpperCase) {
        return JiraKeyUtils.getIssueKeysFromString(logMessageUpperCase);
    }
}
