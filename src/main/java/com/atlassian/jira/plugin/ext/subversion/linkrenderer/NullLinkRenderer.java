/*
 * Created by IntelliJ IDEA.
 * User: Mike
 * Date: Sep 30, 2004
 * Time: 1:47:18 PM
 */
package com.atlassian.jira.plugin.ext.subversion.linkrenderer;

import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;

/**
 * Used when the user does not specify any web links for Perforce - just return String values, no links.
 */
public class NullLinkRenderer implements SubversionLinkRenderer {
    public String getRevisionLink(SVNLogEntry revision) {
        return Long.toString(revision.getRevision());
    }

    public String getChangePathLink(SVNLogEntry revision, SVNLogEntryPath logEntryPath) {
        return logEntryPath.getPath();
    }

    public String getCopySrcLink(SVNLogEntry revision, SVNLogEntryPath logEntryPath) {
        return logEntryPath.getCopyPath() + " #" + logEntryPath.getCopyRevision();
    }
}