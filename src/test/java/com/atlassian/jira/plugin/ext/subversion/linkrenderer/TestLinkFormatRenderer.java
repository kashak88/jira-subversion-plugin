package com.atlassian.jira.plugin.ext.subversion.linkrenderer;

import com.atlassian.jira.plugin.ext.subversion.MultipleSubversionRepositoryManager;
import com.atlassian.jira.plugin.ext.subversion.SubversionConstants;
import com.atlassian.jira.plugin.ext.subversion.SubversionManagerImpl;
import com.opensymphony.module.propertyset.map.MapPropertySet;
import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.SystemUtils;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class TestLinkFormatRenderer extends TestCase {

    private Map subversionManagerProperties;

    private String svnRepositoryUri;

    protected LinkFormatRenderer getLinkFormatRenderer() {
        return getLinkFormatRenderer(subversionManagerProperties);
    }

    protected LinkFormatRenderer getLinkFormatRenderer(final Map properties) {
        MapPropertySet propertySet;

        propertySet = new MapPropertySet();
        propertySet.setMap(properties);

        return new LinkFormatRenderer(new SubversionManagerImpl(1, propertySet));
    }

    protected void setUpLocalSvnRepository() throws SVNException, URISyntaxException {
        File pathToLocalSvnRepository;

        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        FSRepositoryFactory.setup();

        pathToLocalSvnRepository = new File(new URI(svnRepositoryUri));
        if (!pathToLocalSvnRepository.exists())
            SVNRepositoryFactory.createLocalRepository(
                    pathToLocalSvnRepository,
                    true,
                    false);
    }

    protected void tearDownLocalSvnRepository() throws IOException, URISyntaxException {
        File pathToLocalSvnRepository;

        pathToLocalSvnRepository = new File(new URI(svnRepositoryUri));
        if (pathToLocalSvnRepository.exists())
            FileUtils.deleteDirectory(pathToLocalSvnRepository);
    }

    protected void setUp() throws Exception {
        File svnRepository;

        super.setUp();

        do {
            svnRepository = new File(SystemUtils.JAVA_IO_TMPDIR, "jira-subversion-plugin-local-svn-repo" + RandomStringUtils.randomAlphanumeric(16));
        } while (svnRepository.exists());

        svnRepositoryUri = new StringBuffer("file://").append(svnRepository.getAbsoluteFile().toURI().getPath()).toString();

        subversionManagerProperties = new HashMap();
        subversionManagerProperties.put(MultipleSubversionRepositoryManager.SVN_ROOT_KEY, svnRepositoryUri);
        subversionManagerProperties.put(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_PATH_KEY, "http://svn.atlassian.com/fisheye/viewrep/public${path}");
        subversionManagerProperties.put(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_CHANGESET, "http://svn.atlassian.com/fisheye/changelog/public?cs=${rev}");
        subversionManagerProperties.put(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_ADDED, "http://svn.atlassian.com/fisheye/viewrep/public${path}?r=${rev}");
        subversionManagerProperties.put(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_MODIFIED, "http://svn.atlassian.com/fisheye/viewrep/public${path}#${rev}");
        subversionManagerProperties.put(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_REPLACED, "http://svn.atlassian.com/fisheye/viewrep/public${path}#${rev}");
        subversionManagerProperties.put(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_DELETED, "http://svn.atlassian.com/fisheye/viewrep/public${path}");

        setUpLocalSvnRepository();
    }

    protected void tearDown() throws Exception {
        tearDownLocalSvnRepository();
        super.tearDown();
    }

    public void testGetCopySrcLink() {
        Map _subversionManagerProperties;
        LinkFormatRenderer linkFormatRenderer;
        String link;
        SVNLogEntry entry;
        SVNLogEntryPath entryPathWithLeadingSlash;
        SVNLogEntryPath entryPathWithoutLeadingSlash;


        _subversionManagerProperties = new HashMap(this.subversionManagerProperties);
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        entry = new SVNLogEntry(null, 1, "dchui", new Date(), "foo");
        entryPathWithLeadingSlash = new SVNLogEntryPath("/foo/bar", SubversionConstants.MODIFICATION, "/foo", -1);
        entryPathWithoutLeadingSlash = new SVNLogEntryPath("/foo/bar", SubversionConstants.MODIFICATION, "foo", -1);

        /* When path starts with a '/' */
        link = linkFormatRenderer.getCopySrcLink(entry, entryPathWithLeadingSlash);
        assertEquals("<a href=\"http://svn.atlassian.com/fisheye/viewrep/public/foo\">/foo</a>", link);

        /* When path does not start with a '/' */
        link = linkFormatRenderer.getCopySrcLink(entry, entryPathWithoutLeadingSlash);
        assertEquals("<a href=\"http://svn.atlassian.com/fisheye/viewrep/public/foo\">/foo</a>", link);

        /* When there is not path format and path starts with a '/' */
        _subversionManagerProperties.remove(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_PATH_KEY);
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        link = linkFormatRenderer.getCopySrcLink(entry, entryPathWithLeadingSlash);
        assertEquals("/foo", link);

        /* When there is not path format and path does not start with a '/' */
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        link = linkFormatRenderer.getCopySrcLink(entry, entryPathWithoutLeadingSlash);
        assertEquals("/foo", link);
    }

    public void testGetRevisionLink() {
        SVNLogEntry revision;
        LinkFormatRenderer linkFormatRenderer;
        String link;
        Map properties;


        revision = new SVNLogEntry(null, 1, "dchui", new Date(), "foo");
        linkFormatRenderer = getLinkFormatRenderer();
        link = linkFormatRenderer.getRevisionLink(revision);

        /* When a changeset format is defined */
        assertEquals("<a href=\"http://svn.atlassian.com/fisheye/changelog/public?cs=1\">#1</a>", link);

        /* When a changeset format is not defined */
        properties = new HashMap(subversionManagerProperties);
        properties.remove(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_CHANGESET);

        linkFormatRenderer = getLinkFormatRenderer(properties);
        link = linkFormatRenderer.getRevisionLink(revision);
        assertEquals("#1", link);
    }

    public void testGetChangeLinkPathOfFilesModified() {
        Map _subversionManagerProperties;
        LinkFormatRenderer linkFormatRenderer;
        String link;
        SVNLogEntry entry;
        SVNLogEntryPath entryPathWithLeadingSlash;
        SVNLogEntryPath entryPathWithoutLeadingSlash;


        _subversionManagerProperties = new HashMap(this.subversionManagerProperties);
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        entry = new SVNLogEntry(null, 1, "dchui", new Date(), "foo");
        entryPathWithLeadingSlash = new SVNLogEntryPath("/foo/bar", SubversionConstants.MODIFICATION, "/foo", -1);
        entryPathWithoutLeadingSlash = new SVNLogEntryPath("/foo/bar", SubversionConstants.MODIFICATION, "foo", -1);

        /* When path starts with a '/' */
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithLeadingSlash);
        assertEquals("<a href=\"http://svn.atlassian.com/fisheye/viewrep/public/foo/bar#1\">/foo/bar</a>", link);

        /* When path does not start with a '/' */
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithoutLeadingSlash);
        assertEquals("<a href=\"http://svn.atlassian.com/fisheye/viewrep/public/foo/bar#1\">/foo/bar</a>", link);

        /* When there is no path format and path starts with a '/' */
        _subversionManagerProperties.remove(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_MODIFIED);
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithLeadingSlash);
        assertEquals("/foo/bar", link);

        /* When there is no path format and path does not start with a '/' */
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithoutLeadingSlash);
        assertEquals("/foo/bar", link);
    }

    public void testGetChangeLinkPathOfFilesAdded() {
        Map _subversionManagerProperties;
        LinkFormatRenderer linkFormatRenderer;
        String link;
        SVNLogEntry entry;
        SVNLogEntryPath entryPathWithLeadingSlash;
        SVNLogEntryPath entryPathWithoutLeadingSlash;


        _subversionManagerProperties = new HashMap(this.subversionManagerProperties);
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        entry = new SVNLogEntry(null, 1, "dchui", new Date(), "foo");
        entryPathWithLeadingSlash = new SVNLogEntryPath("/foo/bar", SubversionConstants.ADDED, "/foo", -1);
        entryPathWithoutLeadingSlash = new SVNLogEntryPath("/foo/bar", SubversionConstants.ADDED, "foo", -1);

        /* When path starts with a '/' */
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithLeadingSlash);
        assertEquals("<a href=\"http://svn.atlassian.com/fisheye/viewrep/public/foo/bar?r=1\">/foo/bar</a>", link);

        /* When path does not start with a '/' */
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithoutLeadingSlash);
        assertEquals("<a href=\"http://svn.atlassian.com/fisheye/viewrep/public/foo/bar?r=1\">/foo/bar</a>", link);

        /* When there is no path format and path starts with a '/' */
        _subversionManagerProperties.remove(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_ADDED);
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithLeadingSlash);
        assertEquals("/foo/bar", link);

        /* When there is no path format and path does not start with a '/' */
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithoutLeadingSlash);
        assertEquals("/foo/bar", link);
    }

    public void testGetChangeLinkPathOfFilesReplaced() {
        Map _subversionManagerProperties;
        LinkFormatRenderer linkFormatRenderer;
        String link;
        SVNLogEntry entry;
        SVNLogEntryPath entryPathWithLeadingSlash;
        SVNLogEntryPath entryPathWithoutLeadingSlash;


        _subversionManagerProperties = new HashMap(this.subversionManagerProperties);
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        entry = new SVNLogEntry(null, 1, "dchui", new Date(), "foo");
        entryPathWithLeadingSlash = new SVNLogEntryPath("/foo/bar", SubversionConstants.REPLACED, "/foo", -1);
        entryPathWithoutLeadingSlash = new SVNLogEntryPath("/foo/bar", SubversionConstants.REPLACED, "foo", -1);

        /* When path starts with a '/' */
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithLeadingSlash);
        assertEquals("<a href=\"http://svn.atlassian.com/fisheye/viewrep/public/foo/bar#1\">/foo/bar</a>", link);

        /* When path does not start with a '/' */
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithoutLeadingSlash);
        assertEquals("<a href=\"http://svn.atlassian.com/fisheye/viewrep/public/foo/bar#1\">/foo/bar</a>", link);

        /* When there is no path format and path starts with a '/' */
        _subversionManagerProperties.remove(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_REPLACED);
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithLeadingSlash);
        assertEquals("/foo/bar", link);

        /* When there is no path format and path does not start with a '/' */
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithoutLeadingSlash);
        assertEquals("/foo/bar", link);
    }

    public void testGetChangeLinkPathOfFilesDeleted() {
        Map _subversionManagerProperties;
        LinkFormatRenderer linkFormatRenderer;
        String link;
        SVNLogEntry entry;
        SVNLogEntryPath entryPathWithLeadingSlash;
        SVNLogEntryPath entryPathWithoutLeadingSlash;


        _subversionManagerProperties = new HashMap(this.subversionManagerProperties);
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        entry = new SVNLogEntry(null, 1, "dchui", new Date(), "foo");
        entryPathWithLeadingSlash = new SVNLogEntryPath("/foo/bar", SubversionConstants.DELETED, "/foo", -1);
        entryPathWithoutLeadingSlash = new SVNLogEntryPath("/foo/bar", SubversionConstants.DELETED, "foo", -1);

        /* When path starts with a '/' */
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithLeadingSlash);
        assertEquals("<a href=\"http://svn.atlassian.com/fisheye/viewrep/public/foo/bar\">/foo/bar</a>", link);

        /* When path does not start with a '/' */
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithoutLeadingSlash);
        assertEquals("<a href=\"http://svn.atlassian.com/fisheye/viewrep/public/foo/bar\">/foo/bar</a>", link);

        /* When there is no path format and path starts with a '/' */
        _subversionManagerProperties.remove(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_DELETED);
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithLeadingSlash);
        assertEquals("/foo/bar", link);

        /* When there is no path format and path does not start with a '/' */
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithoutLeadingSlash);
        assertEquals("/foo/bar", link);
    }

    public void testGetChangeLinkPathOfFilesWithUnknownStatus() {
        Map _subversionManagerProperties;
        LinkFormatRenderer linkFormatRenderer;
        String link;
        SVNLogEntry entry;
        SVNLogEntryPath entryPathWithLeadingSlash;
        SVNLogEntryPath entryPathWithoutLeadingSlash;


        _subversionManagerProperties = new HashMap(this.subversionManagerProperties);
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        entry = new SVNLogEntry(null, 1, "dchui", new Date(), "foo");
        entryPathWithLeadingSlash = new SVNLogEntryPath("/foo/bar", 'X', "/foo", -1);
        entryPathWithoutLeadingSlash = new SVNLogEntryPath("/foo/bar", 'X', "foo", -1);

        /* When path starts with a '/' */
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithLeadingSlash);
        assertEquals("<a href=\"http://svn.atlassian.com/fisheye/viewrep/public/foo/bar#1\">/foo/bar</a>", link);

        /* When path does not start with a '/' */
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithoutLeadingSlash);
        assertEquals("<a href=\"http://svn.atlassian.com/fisheye/viewrep/public/foo/bar#1\">/foo/bar</a>", link);

        /* When there is no path format and path starts with a '/' */
        _subversionManagerProperties.remove(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_REPLACED);
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithLeadingSlash);
        assertEquals("/foo/bar", link);

        /* When there is no path format and path does not start with a '/' */
        linkFormatRenderer = getLinkFormatRenderer(_subversionManagerProperties);
        link = linkFormatRenderer.getChangePathLink(entry, entryPathWithoutLeadingSlash);
        assertEquals("/foo/bar", link);
    }
}
