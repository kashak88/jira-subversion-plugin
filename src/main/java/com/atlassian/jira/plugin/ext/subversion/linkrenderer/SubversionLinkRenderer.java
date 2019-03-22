/*
 * Created by IntelliJ IDEA.
 * User: Mike
 * Date: Sep 30, 2004
 * Time: 1:44:30 PM
 */
package com.atlassian.jira.plugin.ext.subversion.linkrenderer;

import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;

public interface SubversionLinkRenderer {
    String getRevisionLink(SVNLogEntry revision);

    String getChangePathLink(SVNLogEntry revision, SVNLogEntryPath changePath);

    public String getCopySrcLink(SVNLogEntry revision, SVNLogEntryPath changePath);
}