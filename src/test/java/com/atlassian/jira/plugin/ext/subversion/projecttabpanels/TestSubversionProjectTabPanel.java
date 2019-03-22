package com.atlassian.jira.plugin.ext.subversion.projecttabpanels;

import com.atlassian.jira.config.util.IndexPathManager;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.changehistory.ChangeHistoryManager;
import com.atlassian.jira.plugin.ext.subversion.MultipleSubversionRepositoryManager;
import com.atlassian.jira.plugin.ext.subversion.revisions.RevisionIndexer;
import com.atlassian.jira.plugin.projectpanel.ProjectTabPanelModuleDescriptor;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.browse.BrowseContext;
import com.atlassian.jira.project.version.VersionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.BuildUtilsInfo;
import com.atlassian.jira.web.bean.I18nBean;
import org.jmock.Mock;
import org.jmock.cglib.MockObjectTestCase;
import org.tmatesoft.svn.core.SVNLogEntry;

import java.util.Collections;
import java.util.Map;

public class TestSubversionProjectTabPanel extends MockObjectTestCase {

    private Mock mockMultipleSubversionRepositoryManager;

    private Mock mockVersionManager;

    private Mock mockPermissionManager;

    private Mock mockRevisionIndexer;

    private Mock mockBrowseContext;

    private Mock mockProjectTabPanelModuleDescriptor;

    private Mock mockJiraAuthenticationContext;

    private Mock mockProject;

    private Mock mockIndexPathManager;

    private Mock mockBuildUtilsInfo;

    private boolean doesProjectTabModuleDescriptorCtorRequireModuleFactory() {
        try {
            ProjectTabPanelModuleDescriptor.class.getConstructor(
                    JiraAuthenticationContext.class,
                    Class.forName("com.atlassian.plugin.module.ModuleFactory")
            );
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    protected SubversionProjectTabPanel getSubversionProjectTabPanel() {
        SubversionProjectTabPanel subversionProjectTabPanel;

        try {
            mockJiraAuthenticationContext = new Mock(JiraAuthenticationContext.class);
            mockBuildUtilsInfo = new Mock(BuildUtilsInfo.class);
            if (doesProjectTabModuleDescriptorCtorRequireModuleFactory()) {
                mockProjectTabPanelModuleDescriptor = mock(
                        ProjectTabPanelModuleDescriptor.class,
                        new Class[]{JiraAuthenticationContext.class, Class.forName("com.atlassian.plugin.module.ModuleFactory")},
                        new Object[]{mockJiraAuthenticationContext.proxy(), null});
            } else {
                mockProjectTabPanelModuleDescriptor = mock(
                        ProjectTabPanelModuleDescriptor.class,
                        new Class[]{JiraAuthenticationContext.class},
                        new Object[]{mockJiraAuthenticationContext.proxy()});
            }
            mockProjectTabPanelModuleDescriptor.expects(atMostOnce()).method("getHtml").with(eq("view"), isA(Map.class)).will(returnValue("foo"));

            subversionProjectTabPanel = new SubversionProjectTabPanel(
                    (JiraAuthenticationContext) mockJiraAuthenticationContext.proxy(),
                    (MultipleSubversionRepositoryManager) mockMultipleSubversionRepositoryManager.proxy(),
                    (VersionManager) mockVersionManager.proxy(),
                    (PermissionManager) mockPermissionManager.proxy(),
                    (BuildUtilsInfo) mockBuildUtilsInfo.proxy()) {
                @Override
                I18nBean getI18nBean(ApplicationUser user) {
                    return null;
                }

                @Override
                SubversionProjectRevisionAction createProjectRevisionAction(long repoId, SVNLogEntry logEntry) {
                    return new SubversionProjectRevisionAction(logEntry, (MultipleSubversionRepositoryManager) mockMultipleSubversionRepositoryManager.proxy(), null, repoId) {
                        @Override
                        protected String rewriteLogMessage(String logMessageToBeRewritten) {
                            return logMessageToBeRewritten;
                        }
                    };
                }
            };

            subversionProjectTabPanel.init((ProjectTabPanelModuleDescriptor) mockProjectTabPanelModuleDescriptor.proxy());

            return subversionProjectTabPanel;
        } catch (ClassNotFoundException cnfe) {
            fail("Unable to mock ProjectTabPanelModuleDescriptor");
            return null;
        }
    }

    protected void setUp() throws Exception {
        // Mock mockMultipleSubversionRepositoryManager;
        MultipleSubversionRepositoryManager multipleSubversionRepositoryManager;

        super.setUp();

        mockMultipleSubversionRepositoryManager = new Mock(MultipleSubversionRepositoryManager.class);

        mockVersionManager = new Mock(VersionManager.class);
        mockVersionManager.expects(atMostOnce()).method("getVersionsReleased").withAnyArguments().will(returnValue(Collections.EMPTY_LIST));
        mockVersionManager.expects(atMostOnce()).method("getVersionsUnreleased").withAnyArguments().will(returnValue(Collections.EMPTY_LIST));

        mockPermissionManager = new Mock(PermissionManager.class);

        mockMultipleSubversionRepositoryManager = new Mock(MultipleSubversionRepositoryManager.class);
        mockMultipleSubversionRepositoryManager.expects(atLeastOnce()).method("getRepositoryList").withNoArguments().will(returnValue(Collections.EMPTY_LIST));
        multipleSubversionRepositoryManager = (MultipleSubversionRepositoryManager) mockMultipleSubversionRepositoryManager.proxy();

        mockIndexPathManager = new Mock(IndexPathManager.class);

        mockRevisionIndexer = mock(
                TestRevisionIndexer.class,
                new Class[]{
                        MultipleSubversionRepositoryManager.class,
                        VersionManager.class,
                        IssueManager.class,
                        PermissionManager.class,
                        ChangeHistoryManager.class,
                        IndexPathManager.class
                },
                new Object[]{
                        multipleSubversionRepositoryManager,
                        null,
                        null,
                        mockPermissionManager.proxy(),
                        null,
                        mockIndexPathManager.proxy()
                }
        );

        mockProject = new Mock(Project.class);
        mockBrowseContext = mock(BrowseContext.class);

        this.mockMultipleSubversionRepositoryManager = new Mock(MultipleSubversionRepositoryManager.class);
    }

    // TODO: Fix test when the object in JIRA is mockable
    // Tests below had to be commented out because there is an object in JIRA that couldn't be mocked for the test to proceed.
//    public void testGetHtmlWithoutVersion()
//    {
//        Mock mockHttpServletRequest;
//        HttpServletRequest httpServletRequest;
//        SubversionProjectTabPanel subversionProjectTabPanel;
//        String html;
//
//        mockProject.expects(atLeastOnce()).method("getId").withNoArguments().will(returnValue(new Long(1)));
//        mockProject.expects(atLeastOnce()).method("getKey").withNoArguments().will(returnValue("TP"));
//
//        mockBrowseContext.expects(atLeastOnce()).method("getUser").withNoArguments().will(returnValue(null));
//        mockBrowseContext.expects(atLeastOnce()).method("getProject").withNoArguments().will(returnValue(mockProject.proxy()));
//
//        mockHttpServletRequest = new Mock(HttpServletRequest.class);
//        mockHttpServletRequest.expects(once()).method("getParameter").with(eq("selectedVersion")).will(returnValue("-1"));
//        mockHttpServletRequest.expects(once()).method("getParameter").with(eq("pageIndex")).will(returnValue(null));
//        mockHttpServletRequest.expects(once()).method("getParameter").with(eq("pageSize")).will(returnValue(null));
//        httpServletRequest = (HttpServletRequest) mockHttpServletRequest.proxy();
//        ActionContext.setRequest(httpServletRequest);
//
//        mockRevisionIndexer.reset();
//        mockRevisionIndexer.expects(once()).method("getLogEntriesByProject").with(eq("TP"), NULL, ANYTHING, ANYTHING).will(
//                returnValue(EasyMap.build(1L, EasyList.build(new SVNLogEntry(null, 1, "dchui", new Date(), "foo"))))
//        );
//        mockRevisionIndexer.expects(never()).method("getLogEntriesByVersion").withAnyArguments();
//
//        mockMultipleSubversionRepositoryManager.reset();
//        mockMultipleSubversionRepositoryManager.expects(once()).method("getRevisionIndexer").withNoArguments().will(returnValue(mockRevisionIndexer.proxy()));
//
//        mockVersionManager.reset();
//        mockVersionManager.expects(atLeastOnce()).method("getVersionsReleased").withAnyArguments().will(returnValue(Collections.EMPTY_LIST));
//        mockVersionManager.expects(atLeastOnce()).method("getVersionsUnreleased").withAnyArguments().will(returnValue(Collections.EMPTY_LIST));
//
//        subversionProjectTabPanel = getSubversionProjectTabPanel();
//        html = subversionProjectTabPanel.getHtml((BrowseContext) mockBrowseContext.proxy());
//
//        assertEquals("foo", html);
//        mockRevisionIndexer.verify(); /* If this mock passes, we can be assured that the appropriate methods are called and the results are good */
//        ActionContext.setRequest(null);
//    }

//    public void testGetHtmlWithVersion()
//    {
//        Mock mockHttpServletRequest;
//        Mock mockVersion;
//        HttpServletRequest httpServletRequest;
//        SubversionProjectTabPanel subversionProjectTabPanel;
//        String html;
//        Version version;
//
//        mockProject.expects(atLeastOnce()).method("getId").withNoArguments().will(returnValue(new Long(1)));
//        mockProject.expects(atLeastOnce()).method("getKey").withNoArguments().will(returnValue("TP"));
//
//        mockBrowseContext.expects(atLeastOnce()).method("getUser").withNoArguments().will(returnValue(null));
//        mockBrowseContext.expects(atLeastOnce()).method("getProject").withNoArguments().will(returnValue(mockProject.proxy()));
//
//        mockHttpServletRequest = new Mock(HttpServletRequest.class);
//        mockHttpServletRequest.expects(once()).method("getParameter").with(eq("selectedVersion")).will(returnValue("1")); /* 1L as the version number */
//        mockHttpServletRequest.expects(once()).method("getParameter").with(eq("pageIndex")).will(returnValue(null));
//        mockHttpServletRequest.expects(once()).method("getParameter").with(eq("pageSize")).will(returnValue(null));
//        httpServletRequest = (HttpServletRequest) mockHttpServletRequest.proxy();
//        ActionContext.setRequest(httpServletRequest);
//
//        mockVersion = new Mock(Version.class);
//        version = (Version) mockVersion.proxy();
//
//        mockRevisionIndexer.reset();
//        mockRevisionIndexer.expects(never()).method("getLogEntriesByProject").withAnyArguments();
//        mockRevisionIndexer.expects(once()).method("getLogEntriesByVersion").with(same(version), NULL, ANYTHING, ANYTHING).will(returnValue(Collections.EMPTY_MAP));
//
//        mockMultipleSubversionRepositoryManager.reset();
//        mockMultipleSubversionRepositoryManager.expects(once()).method("getRevisionIndexer").withNoArguments().will(returnValue(mockRevisionIndexer.proxy()));
//
//        mockVersionManager.reset();
//        mockVersionManager.expects(atLeastOnce()).method("getVersionsReleased").withAnyArguments().will(returnValue(Collections.EMPTY_LIST));
//        mockVersionManager.expects(atLeastOnce()).method("getVersionsUnreleased").withAnyArguments().will(returnValue(Collections.EMPTY_LIST));
//        mockVersionManager.expects(once()).method("getVersion").with(eq(1L)).will(returnValue(version));
//
//        subversionProjectTabPanel = getSubversionProjectTabPanel();
//        html = subversionProjectTabPanel.getHtml((BrowseContext) mockBrowseContext.proxy());
//
//        assertEquals("foo", html);
//        mockRevisionIndexer.verify(); /* If this mock passes, we can be assured that the appropriate methods are called and the results are good */
//        mockVersionManager.verify(); /* If this mock passes, we are assured the getVersion(Long):Version is called. That means version processing is good */
//
//        ActionContext.setRequest(null);
//    }

//    public void testShowPanelWhenMultipleSubversionManagerIsNotIndexing()
//    {
//        SubversionProjectTabPanel subversionProjectTabPanel;
//        BrowseContext browseContext = (BrowseContext) mockBrowseContext.proxy();
//
//        mockMultipleSubversionRepositoryManager.reset();
//        mockMultipleSubversionRepositoryManager.expects(exactly(2)).method("isIndexingRevisions").withNoArguments().will(returnValue(false));
//
//        mockPermissionManager.reset();
//        mockPermissionManager.expects(never()).method("hasPermission").with(eq(Permissions.VIEW_VERSION_CONTROL), NULL, NULL);
//
//        subversionProjectTabPanel = getSubversionProjectTabPanel();
//
//        assertFalse(subversionProjectTabPanel.showPanel(browseContext)); /* Not indexing and without permissions */
//        assertFalse(subversionProjectTabPanel.showPanel(browseContext)); /* Not indexing but with permissions */
//    }

    public void testShowPanelWhenMultipleSubversionManagerIsIndexing() {
//        SubversionProjectTabPanel subversionProjectTabPanel;
//        BrowseContext browseContext = (BrowseContext) mockBrowseContext.proxy();
//
//        mockMultipleSubversionRepositoryManager.reset();
//        mockMultipleSubversionRepositoryManager.expects(exactly(2)).method("isIndexingRevisions").withNoArguments().will(returnValue(true));
//
//        mockPermissionManager.reset();
//        mockPermissionManager.expects(exactly(2)).method("hasPermission").with(eq(Permissions.VIEW_VERSION_CONTROL), NULL, NULL).will(
//                onConsecutiveCalls(
//                        returnValue(false),
//                        returnValue(true)
//                )
//        );
//
//        mockBrowseContext.expects(atLeastOnce()).method("getUser").will(returnValue(null));
//        mockBrowseContext.expects(atLeastOnce()).method("getProject").will(returnValue(null));
//
//        subversionProjectTabPanel = getSubversionProjectTabPanel();
//
//        assertFalse(subversionProjectTabPanel.showPanel(browseContext)); /* Not indexing and without permissions */
//        assertTrue(subversionProjectTabPanel.showPanel(browseContext)); /* Not indexing but with permissions */
    }

    protected static class TestRevisionIndexer extends RevisionIndexer {

        public TestRevisionIndexer(MultipleSubversionRepositoryManager multipleSubversionRepositoryManager, VersionManager versionManager, IssueManager issueManager, PermissionManager permissionManager, ChangeHistoryManager changeHistoryManager, IndexPathManager indexPathManager) {
            super(multipleSubversionRepositoryManager, versionManager, issueManager, permissionManager, changeHistoryManager, indexPathManager);
        }
    }
}
