/*
 * Created by IntelliJ IDEA.
 * User: Mike
 * Date: Sep 30, 2004
 * Time: 8:14:04 AM
 */
package com.atlassian.jira.plugin.ext.subversion;

import com.atlassian.jira.plugin.ext.subversion.linkrenderer.SubversionLinkRenderer;
import com.opensymphony.module.propertyset.PropertySet;
import org.tmatesoft.svn.core.SVNLogEntry;

public interface SubversionManager {
    void getLogEntries(long revision, final SvnEntryHandler svnEntryHandler);

    SVNLogEntry getLogEntry(long revision);

    long getId();

    String getDisplayName();

    String getRoot();

    String getUsername();

    String getPassword();

    boolean isActive();

    String getInactiveMessage();

    void activate();

    int getRevisioningCacheSize();

    String getPrivateKeyFile();

    ViewLinkFormat getViewLinkFormat();

    SubversionLinkRenderer getLinkRenderer();

    void update(SvnProperties properties);

    PropertySet getProperties();
}