package com.atlassian.jira.plugin.ext.subversion.linkrenderer;

import com.atlassian.jira.plugin.ext.subversion.SubversionConstants;
import junit.framework.TestCase;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;

import java.util.Date;

public class TestNullLinkRenderer extends TestCase {

    protected NullLinkRenderer getNullLinkerRenderer() {
        return new NullLinkRenderer();
    }

    public void testGetRevisionLink() {
        /* NullLinkRenderer always returns the String representation of the revision */
        assertEquals("1", getNullLinkerRenderer().getRevisionLink(
                new SVNLogEntry(null, 1, "dchui", new Date(), "foo")));
    }

    public void testGetChangePathLink() {
        /* NullLinkRenderer always returns the String representation of the revision */
        assertEquals("/foo/bar", getNullLinkerRenderer().getChangePathLink(
                new SVNLogEntry(null, 1, "dchui", new Date(), "foo"),
                new SVNLogEntryPath("/foo/bar", SubversionConstants.MODIFICATION, "/foo", -1)));
    }

    public void testGetCopySrcLink() {
        /* NullLinkRenderer always returns the String representation of the revision */
        assertEquals("/foo #-1", getNullLinkerRenderer().getCopySrcLink(
                new SVNLogEntry(null, 1, "dchui", new Date(), "foo"),
                new SVNLogEntryPath("/foo/bar", SubversionConstants.MODIFICATION, "/foo", -1)));
    }
}
