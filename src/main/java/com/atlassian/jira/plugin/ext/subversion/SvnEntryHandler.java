package com.atlassian.jira.plugin.ext.subversion;

import org.tmatesoft.svn.core.SVNLogEntry;

public interface SvnEntryHandler {
    void handle(final SVNLogEntry logEntry);
}
