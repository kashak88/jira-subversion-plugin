package com.atlassian.jira.plugin.ext.subversion;

import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.module.propertyset.memory.MemoryPropertySet;
import org.jmock.Mock;
import org.jmock.cglib.MockObjectTestCase;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.util.HashMap;

/**
 * Tests the SubversionManagerImpl.
 *
 * @since 0.9.13
 */
public class TestSubversionManagerImpl extends MockObjectTestCase {

    private SubversionManager subversionManager;

    private PropertySet propertySet;

    private SubversionProperties svnProperties;

    private Mock mockSVNRepository;

    protected void setUp() throws Exception {
        super.setUp();
        propertySet = new MemoryPropertySet();
        propertySet.init(new HashMap(), new HashMap());

        svnProperties = new SubversionProperties();
        mockSVNRepository = mock(TestSVNRepository.class,
                new Class[]{SVNURL.class, ISVNSession.class},
                new Object[]{null, null});
    }

    public void testConnectionToRepositoryTestedWithAcceptableTimeouts() {
        mockSVNRepository.expects(once()).method("setAuthenticationManager").with(isA(ISVNAuthenticationManagerDelegator.class));
        mockSVNRepository.expects(once()).method("testConnection").withNoArguments();

        subversionManager = new SubversionManagerImpl(1l, propertySet) {
            @Override
            SVNURL parseSvnUrl() throws SVNException {
                return null;
            }

            @Override
            SVNRepository createRepository(SVNURL url) throws SVNException {
                return (SVNRepository) mockSVNRepository.proxy();
            }

            @Override
            public ViewLinkFormat getViewLinkFormat() {
                return null;
            }
        };

        assertTrue(subversionManager.isActive());
    }

    public void testIndexingOff() {

        propertySet.setString(MultipleSubversionRepositoryManager.SVN_ROOT_KEY, "/root/repo");
        propertySet.setString(MultipleSubversionRepositoryManager.SVN_REPOSITORY_NAME, "name");
        propertySet.setString(MultipleSubversionRepositoryManager.SVN_USERNAME_KEY, "name");
        propertySet.setString(MultipleSubversionRepositoryManager.SVN_PASSWORD_KEY, "password");
        propertySet.setBoolean(MultipleSubversionRepositoryManager.SVN_REVISION_INDEXING_KEY, false);


        subversionManager = new SubversionManagerImpl(1l, propertySet) {
            public ViewLinkFormat getViewLinkFormat() {
                return null;
            }
        };
        // existing behaviour is that we will have a subversion manager with no repo, assert we have the mgr
        assertNotNull(subversionManager);
    }

    /**
     * Anti test for <a href="http://jira.atlassian.com/browse/SVN-190">SVN-190</a>. The problem
     * was caused by view format not being set and forgetting to update the <code>isViewLinkSet</code>
     * property after update is called.
     */
    public void testUpdateWeblinksConfiguration() {
        ViewLinkFormat viewLinkFormat;

        svnProperties.setRevisionIndexing(Boolean.TRUE);
        svnProperties.setRevisioningCacheSize(new Integer(10));

        svnProperties.setViewFormat("${root}/fisheye/viewrep/public${path}");
        svnProperties.setChangeSetFormat("${root}/fisheye/changelog/public?cs=${rev}");
        svnProperties.setFileAddedFormat("${root}/fisheye/viewrep/public${path}?r=${rev}");
        svnProperties.setFileModifiedFormat("${root}/fisheye/viewrep/public${path}#${rev}");
        svnProperties.setFileReplacedFormat("${root}/fisheye/viewrep/public${path}#${rev}");
        svnProperties.setFileDeletedFormat("${root}/fisheye/viewrep/public${path}");

        subversionManager = new SubversionManagerImpl(1, propertySet);
        assertNull(subversionManager.getViewLinkFormat());

        subversionManager.update(svnProperties);

        viewLinkFormat = subversionManager.getViewLinkFormat();
        /* Make sure the method returns a different ViewLinkFormat */
        assertNotNull(viewLinkFormat);

        assertEquals(svnProperties.getViewFormat(), viewLinkFormat.getViewFormat());
        assertEquals(svnProperties.getChangesetFormat(), viewLinkFormat.getChangesetFormat());
        assertEquals(svnProperties.getFileAddedFormat(), viewLinkFormat.getFileAddedFormat());
        assertEquals(svnProperties.getFileModifiedFormat(), viewLinkFormat.getFileModifiedFormat());
        assertEquals(svnProperties.getFileReplacedFormat(), viewLinkFormat.getFileReplacedFormat());
        assertEquals(svnProperties.getFileDeletedFormat(), viewLinkFormat.getFileDeletedFormat());

        /* Update the view link format */

        svnProperties.setViewFormat("fakeViewFormat");
        svnProperties.setChangeSetFormat("fakeChangesetFormat");
        svnProperties.setFileAddedFormat("fakeFileAddedFormat");
        svnProperties.setFileModifiedFormat("fakeFileModifiedFormat");
        svnProperties.setFileReplacedFormat("fakeFileReplacedFormat");
        svnProperties.setFileDeletedFormat("fakeFileDeletedFormat");

        subversionManager.update(svnProperties);

        viewLinkFormat = subversionManager.getViewLinkFormat();
        /* Make sure the method returns a different ViewLinkFormat */
        assertNotNull(viewLinkFormat);

        assertEquals(svnProperties.getViewFormat(), viewLinkFormat.getViewFormat());
        assertEquals(svnProperties.getChangesetFormat(), viewLinkFormat.getChangesetFormat());
        assertEquals(svnProperties.getFileAddedFormat(), viewLinkFormat.getFileAddedFormat());
        assertEquals(svnProperties.getFileModifiedFormat(), viewLinkFormat.getFileModifiedFormat());
        assertEquals(svnProperties.getFileReplacedFormat(), viewLinkFormat.getFileReplacedFormat());
        assertEquals(svnProperties.getFileDeletedFormat(), viewLinkFormat.getFileDeletedFormat());
    }

    /**
     * Anti test for <a href="http://jira.atlassian.com/browse/SVN-190">SVN-190</a>. The problem
     * was caused by view format not being set and forgetting to update the <code>isViewLinkSet</code>
     * property after update is called.
     */
    public void testResetWeblinksConfiguration() {
        svnProperties.setRevisionIndexing(Boolean.TRUE);
        svnProperties.setRevisioningCacheSize(new Integer(10));

        svnProperties.setViewFormat("${root}/fisheye/viewrep/public${path}");
        svnProperties.setChangeSetFormat("${root}/fisheye/changelog/public?cs=${rev}");
        svnProperties.setFileAddedFormat("${root}/fisheye/viewrep/public${path}?r=${rev}");
        svnProperties.setFileModifiedFormat("${root}/fisheye/viewrep/public${path}#${rev}");
        svnProperties.setFileReplacedFormat("${root}/fisheye/viewrep/public${path}#${rev}");
        svnProperties.setFileDeletedFormat("${root}/fisheye/viewrep/public${path}");

        subversionManager = new SubversionManagerImpl(1, propertySet);
        assertNull(subversionManager.getViewLinkFormat());

        subversionManager.update(svnProperties);
        assertNotNull(subversionManager.getViewLinkFormat());

        /* Now, let's reset all the web links field */
        svnProperties = new SubversionProperties();
        svnProperties.setRevisionIndexing(Boolean.TRUE);
        svnProperties.setRevisioningCacheSize(new Integer(10));

        subversionManager.update(svnProperties);

        /* Make sure it is reset */
        assertNull(subversionManager.getViewLinkFormat());
    }

    public static abstract class TestSVNRepository extends SVNRepository {
        protected TestSVNRepository(SVNURL svnurl, ISVNSession isvnSession) {
            super(svnurl, isvnSession);
        }
    }
}

