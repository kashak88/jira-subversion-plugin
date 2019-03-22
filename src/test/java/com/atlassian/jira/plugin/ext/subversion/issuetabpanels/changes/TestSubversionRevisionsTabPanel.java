package com.atlassian.jira.plugin.ext.subversion.issuetabpanels.changes;

import com.atlassian.core.util.map.EasyMap;
import com.atlassian.jira.config.util.IndexPathManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.changehistory.ChangeHistoryManager;
import com.atlassian.jira.issue.tabpanels.GenericMessageAction;
import com.atlassian.jira.plugin.ext.subversion.MultipleSubversionRepositoryManager;
import com.atlassian.jira.plugin.ext.subversion.revisions.RevisionIndexer;
import com.atlassian.jira.project.version.VersionManager;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.util.EasyList;
import com.atlassian.jira.util.velocity.VelocityRequestContextFactory;
import com.atlassian.plugin.webresource.WebResourceManager;
import org.jmock.Mock;
import org.jmock.cglib.MockObjectTestCase;
import org.tmatesoft.svn.core.SVNLogEntry;

import java.util.Collections;
import java.util.Date;
import java.util.List;

public class TestSubversionRevisionsTabPanel extends MockObjectTestCase {

    private Mock mockMultipleSubversionRepositoryManager;

    private Mock mockRevisionIndexer;

    private Mock mockPermissionManager;

    private Mock mockWebResourceManager;

    private Mock mockIndexPathManager;

    private Mock mockVelocityRequestContextFactory;

    SubversionRevisionsTabPanel getSubversionRevisionsTabPanel() {
        SubversionRevisionsTabPanel subversionRevisionsTabPanel;

        subversionRevisionsTabPanel = new SubversionRevisionsTabPanel(
                (MultipleSubversionRepositoryManager) mockMultipleSubversionRepositoryManager.proxy(),
                (PermissionManager) mockPermissionManager.proxy(),
                (WebResourceManager) mockWebResourceManager.proxy(),
                (VelocityRequestContextFactory) mockVelocityRequestContextFactory.proxy()) {
            @Override
            String getText(String key) {
                return key;
            }

            @Override
            SubversionRevisionAction createSubversionRevisionAction(long repoId, SVNLogEntry logEntry) {
                return new SubversionRevisionAction(logEntry, (MultipleSubversionRepositoryManager) mockMultipleSubversionRepositoryManager.proxy(), descriptor, repoId) {
                    protected String rewriteLogMessage(String logMessageToBeRewritten) {
                        return logMessageToBeRewritten;
                    }
                };
            }

            @Override
            SubversionRevisionAction createLastSubversionRevisionActionInPage(long repoId, SVNLogEntry logEntry) {
                return createSubversionRevisionAction(repoId, logEntry);
            }

//            @Override
//            boolean isSortingActionsInAscendingOrder()
//            {
//                return true;
//            }
        };

        return subversionRevisionsTabPanel;
    }

    protected void setUp() throws Exception {
        MultipleSubversionRepositoryManager multipleSubversionRepositoryManager;

        super.setUp();

        mockPermissionManager = new Mock(PermissionManager.class);

        mockMultipleSubversionRepositoryManager = new Mock(MultipleSubversionRepositoryManager.class);
        mockMultipleSubversionRepositoryManager.expects(atLeastOnce()).method("getRepositoryList").withNoArguments().will(returnValue(Collections.EMPTY_LIST));
        multipleSubversionRepositoryManager = (MultipleSubversionRepositoryManager) mockMultipleSubversionRepositoryManager.proxy();

        mockWebResourceManager = new Mock(WebResourceManager.class);

        mockIndexPathManager = new Mock(IndexPathManager.class);

        mockVelocityRequestContextFactory = new Mock(VelocityRequestContextFactory.class);

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
    }

    public void testGetActionsWhenIssueHasNullSvnLogEntries() {
        Mock mockIssue;
        RevisionIndexer revisionIndexer;
        Issue issue;
        SubversionRevisionsTabPanel subversionRevisionsTabPanel;
        List actions;

        mockIssue = new Mock(Issue.class);
        mockIssue.expects(once()).method("getKey").withNoArguments().will(returnValue("TST-1"));
        issue = (Issue) mockIssue.proxy();

        mockRevisionIndexer.reset();
        mockRevisionIndexer.expects(once()).method("getLogEntriesByRepository").with(same(issue), eq(0), ANYTHING, ANYTHING).will(returnValue(null));
        revisionIndexer = (RevisionIndexer) mockRevisionIndexer.proxy();

        mockMultipleSubversionRepositoryManager.reset();
        mockMultipleSubversionRepositoryManager.expects(once()).method("getRevisionIndexer").withNoArguments().will(returnValue(revisionIndexer));

        mockWebResourceManager.expects(once()).method("requireResource").with(ANYTHING);

        subversionRevisionsTabPanel = getSubversionRevisionsTabPanel();
        actions = subversionRevisionsTabPanel.getActions(issue, null);

        assertNotNull(actions);
        assertEquals(1, actions.size()); /* List should only have a GenericMessageAction element */
        assertTrue(GenericMessageAction.class.isAssignableFrom(actions.get(0).getClass()));
    }

    public void testGetActionsWhenIssueHasEmptySvnLogEntries() {
        Mock mockIssue;
        RevisionIndexer revisionIndexer;
        Issue issue;
        SubversionRevisionsTabPanel subversionRevisionsTabPanel;
        List actions;

        mockIssue = new Mock(Issue.class);
        mockIssue.expects(once()).method("getKey").withNoArguments().will(returnValue("TST-1"));
        issue = (Issue) mockIssue.proxy();

        mockRevisionIndexer.reset();
        mockRevisionIndexer.expects(once()).method("getLogEntriesByRepository").with(same(issue), eq(0), ANYTHING, ANYTHING).will(returnValue(Collections.EMPTY_MAP));
        revisionIndexer = (RevisionIndexer) mockRevisionIndexer.proxy();

        mockMultipleSubversionRepositoryManager.reset();
        mockMultipleSubversionRepositoryManager.expects(once()).method("getRevisionIndexer").withNoArguments().will(returnValue(revisionIndexer));

        mockWebResourceManager.expects(once()).method("requireResource").with(ANYTHING);

        subversionRevisionsTabPanel = getSubversionRevisionsTabPanel();
        actions = subversionRevisionsTabPanel.getActions(issue, null);

        assertNotNull(actions);
        assertEquals(1, actions.size()); /* List should only have a GenericMessageAction element */
        assertTrue(GenericMessageAction.class.isAssignableFrom(actions.get(0).getClass()));
    }

    public void testGetActionsWhenIssueHasSomeSvnLogEntries() {
        Mock mockIssue;
        RevisionIndexer revisionIndexer;
        Issue issue;
        SubversionRevisionsTabPanel subversionRevisionsTabPanel;
        List actions;

        mockIssue = new Mock(Issue.class);
        mockIssue.expects(once()).method("getKey").withNoArguments().will(returnValue("TST-1"));
        issue = (Issue) mockIssue.proxy();

        mockRevisionIndexer.reset();
        mockRevisionIndexer.expects(once()).method("getLogEntriesByRepository").with(same(issue), eq(0), ANYTHING, ANYTHING).will(returnValue(
                EasyMap.build(
                        1L, EasyList.build(new SVNLogEntry(null, 1, "dchui", new Date(), "foobar")),
                        2L, EasyList.build(new SVNLogEntry(null, 1, "dchui", new Date(), "foobar"))
                )
        ));
        revisionIndexer = (RevisionIndexer) mockRevisionIndexer.proxy();

        mockMultipleSubversionRepositoryManager.reset();
        mockMultipleSubversionRepositoryManager.expects(once()).method("getRevisionIndexer").withNoArguments().will(returnValue(revisionIndexer));

        mockWebResourceManager.expects(once()).method("requireResource").with(ANYTHING);

        subversionRevisionsTabPanel = getSubversionRevisionsTabPanel();
        actions = subversionRevisionsTabPanel.getActions(issue, null);

        assertNotNull(actions);
        assertEquals(2, actions.size()); /* There should be two instances of SubversionRevisionAction returend */
        assertTrue(SubversionRevisionAction.class.isAssignableFrom(actions.get(0).getClass()));
        assertTrue(SubversionRevisionAction.class.isAssignableFrom(actions.get(1).getClass()));
    }

    public void testShowPanelWhenMultipleSubversionManagerIsNotIndexing() {
        SubversionRevisionsTabPanel subversionRevisionsTabPanel;

        mockMultipleSubversionRepositoryManager.reset();
        mockMultipleSubversionRepositoryManager.expects(exactly(2)).method("isIndexingRevisions").withNoArguments().will(returnValue(false));

        mockPermissionManager.reset();
        mockPermissionManager.expects(never()).method("hasPermission").with(eq(Permissions.VIEW_VERSION_CONTROL), NULL, NULL);

        subversionRevisionsTabPanel = getSubversionRevisionsTabPanel();

        assertFalse(subversionRevisionsTabPanel.showPanel(null, null)); /* Not indexing and without permissions */
        assertFalse(subversionRevisionsTabPanel.showPanel(null, null)); /* Not indexing but with permissions */
    }

    public void testShowPanelWhenMultipleSubversionManagerIsIndexing() {
        SubversionRevisionsTabPanel subversionRevisionsTabPanel;

        mockMultipleSubversionRepositoryManager.reset();
        mockMultipleSubversionRepositoryManager.expects(exactly(2)).method("isIndexingRevisions").withNoArguments().will(returnValue(true));

        mockPermissionManager.reset();
        mockPermissionManager.expects(exactly(2)).method("hasPermission").with(eq(Permissions.VIEW_VERSION_CONTROL), NULL, NULL).will(
                onConsecutiveCalls(
                        returnValue(false),
                        returnValue(true)
                )
        );

        subversionRevisionsTabPanel = getSubversionRevisionsTabPanel();

        assertFalse(subversionRevisionsTabPanel.showPanel(null, null)); /* Not indexing and without permissions */
        assertTrue(subversionRevisionsTabPanel.showPanel(null, null)); /* Not indexing but with permissions */
    }

    protected static class TestRevisionIndexer extends RevisionIndexer {

        public TestRevisionIndexer(MultipleSubversionRepositoryManager multipleSubversionRepositoryManager, VersionManager versionManager, IssueManager issueManager, PermissionManager permissionManager, ChangeHistoryManager changeHistoryManager, IndexPathManager indexPathManager) {
            super(multipleSubversionRepositoryManager, versionManager, issueManager, permissionManager, changeHistoryManager, indexPathManager);
        }
    }
}
