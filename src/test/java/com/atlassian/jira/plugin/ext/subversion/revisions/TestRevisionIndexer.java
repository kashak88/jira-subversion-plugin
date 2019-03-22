package com.atlassian.jira.plugin.ext.subversion.revisions;

import com.atlassian.jira.config.util.IndexPathManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.changehistory.ChangeHistoryManager;
import com.atlassian.jira.issue.index.IndexException;
import com.atlassian.jira.mock.issue.MockIssue;
import com.atlassian.jira.plugin.ext.subversion.MultipleSubversionRepositoryManager;
import com.atlassian.jira.plugin.ext.subversion.SubversionManager;
import com.atlassian.jira.plugin.ext.subversion.SvnEntryHandler;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.project.version.VersionManager;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.util.EasyList;
import com.google.common.collect.ImmutableList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.lucene.document.DateField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.jmock.Mock;
import org.jmock.cglib.MockObjectTestCase;
import org.jmock.core.Invocation;
import org.jmock.core.constraint.IsAnything;
import org.jmock.core.stub.CustomStub;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class TestRevisionIndexer extends MockObjectTestCase {

    private static final String MY_BS_INDEX_PATH = "mybsindexpath";

    private static final String MY_NEW_BS_INDEX_PATH = "mynewbsindexpath";

    private Mock mockMultipleSubversionRepositoryManager;

    private MultipleSubversionRepositoryManager multipleSubversionRepositoryManager;

    private Mock mockSvnMgr;

    private SubversionManager svnMgr;

    private Mock mockPermissionManager;

    private PermissionManager permissionManager;

    private ChangeHistoryManager changeHistoryManager;

    private Mock mockChangeHistoryManager;

    private Mock mockIssueManager;

    private IssueManager issueManager;

    private Mock mockVersionManager;

    private VersionManager versionManager;

    private File temporaryIndexDirectory;

    private IndexPathManager indexPathManager;

    private Mock mockIndexPathManager;

    private String getIndexPath() {
        return new StringBuffer(temporaryIndexDirectory.getAbsolutePath()).append(SystemUtils.FILE_SEPARATOR).append(RevisionIndexer.REVISIONS_INDEX_DIRECTORY).toString();
    }

    protected void setUp() throws Exception {
        super.setUp();

        /* Setup temporary index directory */
        do {
            temporaryIndexDirectory = new File(SystemUtils.JAVA_IO_TMPDIR, RandomStringUtils.randomAlphanumeric(16));
        }
        while (temporaryIndexDirectory.exists());
        temporaryIndexDirectory.mkdirs();


        mockIndexPathManager = new Mock(IndexPathManager.class);
        mockIndexPathManager.expects(atLeastOnce()).method("getPluginIndexRootPath").withNoArguments().will(returnValue(temporaryIndexDirectory.getAbsolutePath()));
        indexPathManager = (IndexPathManager) mockIndexPathManager.proxy();

        mockMultipleSubversionRepositoryManager = new Mock(MultipleSubversionRepositoryManager.class);
        mockMultipleSubversionRepositoryManager
                .expects(atLeastOnce())
                .method("getRepositoryList")
                .withNoArguments()
                .will(returnValue(Collections.EMPTY_LIST));
        multipleSubversionRepositoryManager = (MultipleSubversionRepositoryManager) mockMultipleSubversionRepositoryManager.proxy();

        mockSvnMgr = new Mock(SubversionManager.class);
        svnMgr = (SubversionManager) mockSvnMgr.proxy();

        mockPermissionManager = new Mock(PermissionManager.class);
        permissionManager = (PermissionManager) mockPermissionManager.proxy();

        List<String> previousIssueKeys = new ArrayList<String>();
        previousIssueKeys.add("APR-137");
        previousIssueKeys.add("FPR-49");
        mockChangeHistoryManager = new Mock(ChangeHistoryManager.class);
        mockChangeHistoryManager
                .expects(atMostOnce())
                .method("getPreviousIssueKeys")
                .will(returnValue(previousIssueKeys));
        changeHistoryManager = (ChangeHistoryManager) mockChangeHistoryManager.proxy();

        mockIssueManager = new Mock(IssueManager.class);
        issueManager = (IssueManager) mockIssueManager.proxy();

        mockVersionManager = new Mock(VersionManager.class);
        versionManager = (VersionManager) mockVersionManager.proxy();
    }

    protected void tearDown() throws Exception {
        if (temporaryIndexDirectory.exists()) {
            try {
                FileUtils.deleteDirectory(temporaryIndexDirectory);
            } catch (final IOException ioe) {
                ioe.printStackTrace();
            }
        }
        super.tearDown();
    }

    protected RevisionIndexer getRevisionIndexer() {
        return new RevisionIndexer(
                multipleSubversionRepositoryManager,
                versionManager, issueManager, permissionManager, changeHistoryManager, indexPathManager);
    }

    // This is here for the fix for SVN-123
    public void testIndexDirectoryNotCached() {
        mockIndexPathManager.reset();
        mockIndexPathManager.expects(exactly(3)).method("getPluginIndexRootPath").withNoArguments().will(
                onConsecutiveCalls(
                        returnValue(MY_BS_INDEX_PATH),
                        returnValue(MY_NEW_BS_INDEX_PATH), // Updated index path
                        returnValue(MY_NEW_BS_INDEX_PATH) // Updated index path
                )
        );

        RevisionIndexer revisionIndexer = getRevisionIndexer();
        assertEquals(MY_BS_INDEX_PATH + System.getProperty("file.separator") + RevisionIndexer.REVISIONS_INDEX_DIRECTORY, revisionIndexer.getIndexPath());

        assertEquals(MY_NEW_BS_INDEX_PATH + System.getProperty("file.separator") + RevisionIndexer.REVISIONS_INDEX_DIRECTORY, revisionIndexer.getIndexPath());
        assertNotSame(MY_BS_INDEX_PATH + System.getProperty("file.separator") + RevisionIndexer.REVISIONS_INDEX_DIRECTORY, revisionIndexer.getIndexPath());
    }

    public void testUpdateIndexWithNoIndexDirectorWhenEverythingIsOk() throws IOException, IndexException {
        RevisionIndexer revisionIndexer;

        /* Setup the repository to return one svn entry (revision 0) */
        final List<SVNLogEntry> entries = ImmutableList.of(new SVNLogEntry(Collections.EMPTY_MAP, 0, "dchui", new Date(), "TST-1"));

        mockSvnMgr.expects(atLeastOnce()).method("getId").withNoArguments().will(returnValue(1L));
        mockSvnMgr.expects(atLeastOnce()).method("isActive").withNoArguments().will(returnValue(true));
        mockSvnMgr.expects(atLeastOnce()).method("getLogEntries").with(eq(-1L), ANYTHING).will(callHandlerWithLogEntries(entries));

        mockMultipleSubversionRepositoryManager.reset();
        mockMultipleSubversionRepositoryManager
                .expects(atLeastOnce())
                .method("getRepositoryList")
                .withNoArguments()
                .will(returnValue(EasyList.build(svnMgr)));

        revisionIndexer = new RevisionIndexer(multipleSubversionRepositoryManager,
                null, null, null, null, indexPathManager) {
            protected boolean isKeyInString(SVNLogEntry logEntry) {
                return true;
            }

            protected List<String> getIssueKeysFromString(SVNLogEntry logEntry) {
                return EasyList.build("TST-1");
            }

            protected String getProjectKeyFromIssueKey(String issueKey) {
                return "TST";
            }
        };
        revisionIndexer.updateIndex();

        /* Check if a document gets indexed */
        IndexReader indexReader = new DefaultLuceneIndexAccessor().getIndexReader(revisionIndexer.getIndexPath());
        assertEquals(1, indexReader.numDocs());
        indexReader.close();
    }

    public void testUpdateIndexWithNoIndexDirectoryWhenEntryHasEmptyCommitMessage() throws IOException, IndexException {
        RevisionIndexer revisionIndexer;

        /* Setup the repository to return one svn entry (revision 0) */
        final List<SVNLogEntry> entries = ImmutableList.of(new SVNLogEntry(Collections.EMPTY_MAP, 0, "dchui", new Date(), StringUtils.EMPTY));

        mockSvnMgr.expects(atLeastOnce()).method("getId").withNoArguments().will(returnValue(1L));
        mockSvnMgr.expects(atLeastOnce()).method("isActive").withNoArguments().will(returnValue(true));
        mockSvnMgr.expects(atLeastOnce()).method("getLogEntries").with(eq(-1L), ANYTHING).will(callHandlerWithLogEntries(entries));

        mockMultipleSubversionRepositoryManager.reset();
        mockMultipleSubversionRepositoryManager
                .expects(atLeastOnce())
                .method("getRepositoryList")
                .withNoArguments()
                .will(returnValue(EasyList.build(svnMgr)));

        revisionIndexer = getRevisionIndexer();
        revisionIndexer.updateIndex();

        /* Check nothing gets indexed */
        IndexReader indexReader = new DefaultLuceneIndexAccessor().getIndexReader(revisionIndexer.getIndexPath());
        assertEquals(0, indexReader.numDocs());
        indexReader.close();
    }

    public void testUpdateIndexWithNoIndexDirectoryWhenEntryCommitMessageHasNoJiraKeys() throws IOException, IndexException {
        RevisionIndexer revisionIndexer;

        /* Setup the repository to return one svn entry (revision 0) */
        final List<SVNLogEntry> entries = ImmutableList.of(new SVNLogEntry(Collections.EMPTY_MAP, 0, "dchui", new Date(), "There is no JIRA key here."));

        mockSvnMgr.expects(atLeastOnce()).method("getId").withNoArguments().will(returnValue(1L));
        mockSvnMgr.expects(atLeastOnce()).method("isActive").withNoArguments().will(returnValue(true));
        mockSvnMgr.expects(atLeastOnce()).method("getLogEntries").with(eq(-1L), ANYTHING).will(callHandlerWithLogEntries(entries));

        mockMultipleSubversionRepositoryManager.reset();
        mockMultipleSubversionRepositoryManager
                .expects(atLeastOnce())
                .method("getRepositoryList")
                .withNoArguments()
                .will(returnValue(EasyList.build(svnMgr)));

        revisionIndexer = new RevisionIndexer(multipleSubversionRepositoryManager,
                null, null, null, null, indexPathManager) {
            protected boolean isKeyInString(SVNLogEntry logEntry) {
                return false;
            }
        };
        revisionIndexer.updateIndex();

        /* Check nothing gets indexed */
        IndexReader indexReader = new DefaultLuceneIndexAccessor().getIndexReader(revisionIndexer.getIndexPath());
        assertEquals(0, indexReader.numDocs());
        indexReader.close();
    }


    /**
     * Tests that the RevisionIndexer will handle each repository even if method calls on prior repositories throw
     * exceptions.
     *
     * @throws Exception wheneva
     */
    public void testUpdateIndexRepositoryThrowsException() throws Exception {
        Mock mockSubversionManagerExploding;
        Mock mockSubversionManagerNonExploding;

        /* The exploding SubversionManager */
        mockSubversionManagerExploding = new Mock(SubversionManager.class);
        mockSubversionManagerExploding.expects(atLeastOnce()).method("getId").withNoArguments().will(returnValue(1L));
        mockSubversionManagerExploding.expects(atLeastOnce()).method("isActive").withNoArguments().will(returnValue(true));
        mockSubversionManagerExploding.expects(atLeastOnce()).method("getDisplayName").withNoArguments().will(returnValue("Repository 1"));
        mockSubversionManagerExploding
                .expects(once())
                .method("getLogEntries")
                .withAnyArguments()
                .will(throwException(new RuntimeException("Exploding MockSubversionManager has exploded.")));

        /* The non exploding SubversionManager */
        mockSubversionManagerNonExploding = new Mock(SubversionManager.class);
        mockSubversionManagerNonExploding.expects(atLeastOnce()).method("getId").withNoArguments().will(returnValue(1L));
        mockSubversionManagerNonExploding.expects(atLeastOnce()).method("isActive").withNoArguments().will(returnValue(true));
//        mockSubversionManagerNonExploding.expects(atLeastOnce()).method("getDisplayName").withNoArguments().will(returnValue("Repository 2"));
        mockSubversionManagerNonExploding
                .expects(once())
                .method("getLogEntries")
                .withAnyArguments();

        mockMultipleSubversionRepositoryManager.reset();
        mockMultipleSubversionRepositoryManager
                .expects(atLeastOnce())
                .method("getRepositoryList")
                .withNoArguments()
                .will(returnValue(EasyList.build(mockSubversionManagerExploding.proxy(), mockSubversionManagerNonExploding.proxy())));

        // instantiate class under test
        RevisionIndexer ri = new RevisionIndexer(
                (MultipleSubversionRepositoryManager) mockMultipleSubversionRepositoryManager.proxy(),
                null, null, null, null, indexPathManager) {
            protected Long getLatestIndexedRevision(long repoId) {
                return 123L;
            }
        };

        try {
            ri.updateIndex();
        } catch (RuntimeException e) {
            fail("The exception thrown from one repository should not propagate out of the all-repo index update method.");
        }
        // from this test point of view we don't really care which methods are called on the repo, if any were called,
        // then the RevisionIndexer didn't catch exceptions outside the while loop
//        assertTrue("The RevisionIndexer probably blew up attempting to index the first repo (SVN-158)", mildManneredRepo.isMethodsCalled());
        mockSubversionManagerNonExploding.verify();
        mockMultipleSubversionRepositoryManager.verify();
    }

    public void testGetLogEntriesByRepositoryWhenIndexDirectoryDoesNotExist() throws IOException, IndexException {
        Mock mockIssue;
        Issue issue;
        RevisionIndexer revisionIndexer;

        mockIssue = new Mock(Issue.class);
        mockIssue.expects(atLeastOnce()).method("getKey").withNoArguments().will(returnValue("TSTS-1"));
        issue = (Issue) mockIssue.proxy();

        mockIndexPathManager.reset();
        mockIndexPathManager
                .expects(atLeastOnce())
                .method("getPluginIndexRootPath")
                .withNoArguments()
                .will(returnValue(temporaryIndexDirectory.getAbsolutePath() + "-aPrefixMakingItAnInvalidDirectory"));

        revisionIndexer = getRevisionIndexer();
        assertNull(revisionIndexer.getLogEntriesByRepository(issue));
    }

    public void testGetLogEntriesByRepositoryWhenIssueKeyDoesNotMatch() throws IOException, IndexException {
        Mock mockIssue;
        Issue issue;
        RevisionIndexer revisionIndexer;
        IndexWriter indexWriter;
        Document doc;

        /* Setup the issue to be used in the query */
        mockIssue = new Mock(Issue.class);
        mockIssue.expects(atLeastOnce()).method("getId").withNoArguments().will(returnValue(new Long(10000L)));
        mockIssue.expects(atLeastOnce()).method("getKey").withNoArguments().will(returnValue("TST-1"));
        issue = (Issue) mockIssue.proxy();

        /* Populate the search index */
        indexWriter = new DefaultLuceneIndexAccessor().getIndexWriter(getIndexPath(), true, RevisionIndexer.ANALYZER);

        doc = new Document();
        doc.add(new Field(RevisionIndexer.FIELD_ISSUEKEY, "TST-2", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_DATE, DateField.dateToString(new Date()), Field.Store.YES, Field.Index.NOT_ANALYZED));
        indexWriter.addDocument(doc);
        indexWriter.commit();
        indexWriter.close();

        revisionIndexer = getRevisionIndexer();
        assertEquals(0, revisionIndexer.getLogEntriesByRepository(issue).size());
    }

    public void testGetLogEntriesByRepositoryWhenRevisionCannotBeFoundInRepository() throws IOException, IndexException {
        Mock mockIssue;
        Issue issue;
        RevisionIndexer revisionIndexer;
        IndexWriter indexWriter;
        Document doc;

        /* Setup the issue to be used in the query */
        mockIssue = new Mock(Issue.class);
        mockIssue.expects(atLeastOnce()).method("getId").withNoArguments().will(returnValue(new Long(10000L)));
        mockIssue.expects(atLeastOnce()).method("getKey").withNoArguments().will(returnValue("TST-1"));
        issue = (Issue) mockIssue.proxy();

        /* Populate the search index */
        indexWriter = new DefaultLuceneIndexAccessor().getIndexWriter(getIndexPath(), true, RevisionIndexer.ANALYZER);

        doc = new Document();
        doc.add(new Field(RevisionIndexer.FIELD_ISSUEKEY, "TST-1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_REPOSITORY, "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_REVISIONNUMBER, "0", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_DATE, DateField.dateToString(new Date()), Field.Store.YES, Field.Index.NOT_ANALYZED));
        indexWriter.addDocument(doc);
        indexWriter.commit();
        indexWriter.close();

        mockSvnMgr.expects(once()).method("getLogEntry").with(eq(0L)).will(returnValue(null));
        mockMultipleSubversionRepositoryManager
                .expects(once())
                .method("getRepository")
                .with(eq(1L))
                .will(returnValue(svnMgr));

        revisionIndexer = getRevisionIndexer();
        assertEquals(0, revisionIndexer.getLogEntriesByRepository(issue).size());
    }

    public void testGetLogEntriesByRepositorySorted() throws IOException, IndexException {
        Mock mockIssue;
        Issue issue;
        RevisionIndexer revisionIndexer;
        IndexWriter indexWriter;
        Document doc;

        /* Setup the issue to be used in the query */
        mockIssue = new Mock(Issue.class);
        mockIssue.expects(atLeastOnce()).method("getId").withNoArguments().will(returnValue(new Long(10000L)));
        mockIssue.expects(atLeastOnce()).method("getKey").withNoArguments().will(returnValue("TST-1"));
        issue = (Issue) mockIssue.proxy();

        /* Populate the search index */
        indexWriter = new DefaultLuceneIndexAccessor().getIndexWriter(getIndexPath(), true, RevisionIndexer.ANALYZER);

        long nowInMillis = System.currentTimeMillis();

        /* This is the first matching document with revision 1 */
        doc = new Document();
        doc.add(new Field(RevisionIndexer.FIELD_ISSUEKEY, "TST-1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_REPOSITORY, "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_REVISIONNUMBER, "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_DATE, DateField.dateToString(new Date(nowInMillis + 1000)), Field.Store.YES, Field.Index.NOT_ANALYZED));
        indexWriter.addDocument(doc);

        /* This is the second matching document with revision 0 */
        doc = new Document();
        doc.add(new Field(RevisionIndexer.FIELD_ISSUEKEY, "TST-1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_REPOSITORY, "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_REVISIONNUMBER, "0", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_DATE, DateField.dateToString(new Date(nowInMillis)), Field.Store.YES, Field.Index.NOT_ANALYZED));
        indexWriter.addDocument(doc);

        indexWriter.commit();
        indexWriter.close();

        SVNLogEntry firstCommit = new SVNLogEntry(Collections.EMPTY_MAP, 0, "dchui", new Date(), "TST-1 First commit");
        SVNLogEntry secondCommit = new SVNLogEntry(Collections.EMPTY_MAP, 1, "dchui", new Date(), "TST-1 Second commmit");

        mockSvnMgr.expects(exactly(2)).method("getLogEntry").with(isA(Long.class)).will(
                onConsecutiveCalls(
                        returnValue(firstCommit),
                        returnValue(secondCommit)
                )
        );
        mockMultipleSubversionRepositoryManager
                .expects(exactly(2))
                .method("getRepository")
                .with(eq(1L))
                .will(returnValue(svnMgr));

        Map entriesMap;
        List entries;
        revisionIndexer = getRevisionIndexer();

        entriesMap = revisionIndexer.getLogEntriesByRepository(issue);
        assertEquals(1, entriesMap.size());

        entries = (List) entriesMap.get(1L); /* Get entries from repository with ID 1 */

        assertEquals(2, entries.size());
        /* Earlier commits go on top */
        assertSame(firstCommit, entries.get(0));
        assertSame(secondCommit, entries.get(1));
    }

    public void testGetLogEntriesByProjectWhenUserHasNoPermissionToViewAnyIssuesMentionedInSvnCommit() throws IOException, IndexException {
        Mock mockIssue;
        Issue issue;
        IndexWriter indexWriter;
        Document doc;
        RevisionIndexer revisionIndexer;

        /* Populate the search index */
        indexWriter = new DefaultLuceneIndexAccessor().getIndexWriter(getIndexPath(), true, RevisionIndexer.ANALYZER);

        /* This is the first matching document with revision 1 */
        doc = new Document();
        doc.add(new Field(RevisionIndexer.FIELD_PROJECTKEY, "TST", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_ISSUEKEY, "TST-1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_REPOSITORY, "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_REVISIONNUMBER, "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_DATE, DateField.dateToString(new Date()), Field.Store.YES, Field.Index.NOT_ANALYZED));
        indexWriter.addDocument(doc);

        indexWriter.close();

        mockIssue = new Mock(MutableIssue.class);
        issue = (Issue) mockIssue.proxy();

        mockIssueManager.expects(once()).method("getIssueObject").with(eq("TST-1")).will(returnValue(issue));
        mockPermissionManager.expects(once()).method("hasPermission")
                .with(eq(Permissions.VIEW_VERSION_CONTROL), same(issue), new IsAnything())
                .will(returnValue(false));

        Map entriesMap;

        revisionIndexer = getRevisionIndexer();
        entriesMap = revisionIndexer.getLogEntriesByProject("TST", null, 0, Integer.MAX_VALUE);

        assertEquals(0, entriesMap.size());
    }

    public void testGetLogEntriesByProjectWhenUserHasPermissionToViewTheIssuesMentionedInSvnCommitButRevisionCannotBeFoundInAnyRepository() throws IOException, IndexException {
        Mock mockIssue;
        Issue issue;
        IndexWriter indexWriter;
        Document doc;
        RevisionIndexer revisionIndexer;

        /* Populate the search index */
        indexWriter = new DefaultLuceneIndexAccessor().getIndexWriter(getIndexPath(), true, RevisionIndexer.ANALYZER);

        /* This is the first matching document with revision 1 */
        doc = new Document();
        doc.add(new Field(RevisionIndexer.FIELD_PROJECTKEY, "TST", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_ISSUEKEY, "TST-1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_REPOSITORY, "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_REVISIONNUMBER, "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_DATE, DateField.dateToString(new Date()), Field.Store.YES, Field.Index.NOT_ANALYZED));
        indexWriter.addDocument(doc);

        indexWriter.close();

        mockIssue = new Mock(MutableIssue.class);
        issue = (Issue) mockIssue.proxy();

        mockIssueManager.expects(once()).method("getIssueObject").with(eq("TST-1")).will(returnValue(issue));
        mockPermissionManager.expects(once()).method("hasPermission")
                .with(eq(Permissions.VIEW_VERSION_CONTROL), same(issue), new IsAnything())
                .will(returnValue(true));

        mockSvnMgr.expects(once()).method("getLogEntry").with(eq(1L)).will(returnValue(null));
        mockMultipleSubversionRepositoryManager.expects(once()).method("getRepository").with(eq(1L)).will(returnValue(svnMgr));

        Map entriesMap;

        revisionIndexer = getRevisionIndexer();
        entriesMap = revisionIndexer.getLogEntriesByProject("TST", null, 0, Integer.MAX_VALUE);

        assertEquals(0, entriesMap.size());
    }

    public void testGetLogEntriesByProjectWhenEverythingIsOk() throws IOException, IndexException {
        Mock mockIssue;
        Issue issue;
        IndexWriter indexWriter;
        Document doc;
        RevisionIndexer revisionIndexer;
        SVNLogEntry svnLogEntry;

        /* Populate the search index */
        indexWriter = new DefaultLuceneIndexAccessor().getIndexWriter(getIndexPath(), true, RevisionIndexer.ANALYZER);

        /* This is the first matching document with revision 1 */
        doc = new Document();
        doc.add(new Field(RevisionIndexer.FIELD_PROJECTKEY, "TST", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_ISSUEKEY, "TST-1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_REPOSITORY, "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_REVISIONNUMBER, "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_DATE, DateField.dateToString(new Date()), Field.Store.YES, Field.Index.NOT_ANALYZED));
        indexWriter.addDocument(doc);

        indexWriter.close();

        mockIssue = new Mock(MutableIssue.class);
        issue = (Issue) mockIssue.proxy();

        mockIssueManager.expects(once()).method("getIssueObject").with(eq("TST-1")).will(returnValue(issue));
        mockPermissionManager.expects(once()).method("hasPermission")
                .with(eq(Permissions.VIEW_VERSION_CONTROL), same(issue), new IsAnything())
                .will(returnValue(true));

        svnLogEntry = new SVNLogEntry(Collections.EMPTY_MAP, 1L, "dchui", new Date(), "TST-1");
        mockSvnMgr.expects(once()).method("getLogEntry").with(eq(1L)).will(returnValue(svnLogEntry));
        mockMultipleSubversionRepositoryManager.expects(once()).method("getRepository").with(eq(1L)).will(returnValue(svnMgr));

        Map entriesMap;
        List entries;

        revisionIndexer = getRevisionIndexer();
        entriesMap = revisionIndexer.getLogEntriesByProject("TST", null, 0, Integer.MAX_VALUE);
        entries = (List) entriesMap.get(1L);

        assertEquals(1, entriesMap.size()); /* There should be 1 entry because it matches repository 1 */
        assertEquals(1, entries.size()); /* There should be 1 entry because it matches revision 1 */
        assertSame(svnLogEntry, entries.get(0));
    }

    public void testGetLogEntriesByVersionWhenUserHasNoPermissionToViewAnyIssuesOfVersionSpecified() throws IOException, IndexException {
        Mock mockIssue;
        Issue issueObject;
        IndexWriter indexWriter;
        Document doc;


        Issue inputIssue = new MockIssue(1, "TST-1");
        mockVersionManager.expects(once()).method("getIssuesWithFixVersion").withAnyArguments().will(returnValue(EasyList.build(inputIssue)));
        mockVersionManager.expects(once()).method("getIssuesWithAffectsVersion").withAnyArguments().will(returnValue(EasyList.build(inputIssue)));

        /* Populate the search index */
        indexWriter = new DefaultLuceneIndexAccessor().getIndexWriter(getIndexPath(), true, RevisionIndexer.ANALYZER);

        /* This is the first matching document with revision 1 */
        doc = new Document();
        doc.add(new Field(RevisionIndexer.FIELD_ISSUEKEY, "TST-1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_REPOSITORY, "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_REVISIONNUMBER, "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_DATE, DateField.dateToString(new Date()), Field.Store.YES, Field.Index.NOT_ANALYZED));
        indexWriter.addDocument(doc);

        indexWriter.close();

        mockIssue = new Mock(MutableIssue.class);
        issueObject = (Issue) mockIssue.proxy();

        mockIssueManager.expects(once()).method("getIssueObject").with(eq("TST-1")).will(returnValue(issueObject));
        mockPermissionManager.expects(once()).method("hasPermission")
                .with(eq(Permissions.VIEW_VERSION_CONTROL), same(issueObject), new IsAnything())
                .will(returnValue(false));

        Map entriesMap;

        RevisionIndexer revisionIndexer = getRevisionIndexer();
        entriesMap = revisionIndexer.getLogEntriesByVersion((Version) new Mock(Version.class).proxy(), null, 0, Integer.MAX_VALUE);

        assertEquals(0, entriesMap.size()); /* No matches */
    }

    public void testGetLogEntriesByVersionWhenEverythingIsOk() throws IOException, IndexException {
        Mock mockIssue;
        Issue issueObject;
        IndexWriter indexWriter;
        Document doc;
        SVNLogEntry svnLogEntry;

        Issue inputIssue = new MockIssue(1, "TST-1");
        mockVersionManager.expects(once()).method("getIssuesWithFixVersion").withAnyArguments().will(returnValue(EasyList.build(inputIssue)));
        mockVersionManager.expects(once()).method("getIssuesWithAffectsVersion").withAnyArguments().will(returnValue(EasyList.build(inputIssue)));

        /* Populate the search index */
        indexWriter = new DefaultLuceneIndexAccessor().getIndexWriter(getIndexPath(), true, RevisionIndexer.ANALYZER);

        /* This is the first matching document with revision 1 */
        doc = new Document();
        doc.add(new Field(RevisionIndexer.FIELD_ISSUEKEY, "TST-1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_REPOSITORY, "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_REVISIONNUMBER, "1", Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(RevisionIndexer.FIELD_DATE, DateField.dateToString(new Date()), Field.Store.YES, Field.Index.NOT_ANALYZED));
        indexWriter.addDocument(doc);

        indexWriter.close();

        mockIssue = new Mock(MutableIssue.class);
        issueObject = (Issue) mockIssue.proxy();

        mockIssueManager.expects(once()).method("getIssueObject").with(eq("TST-1")).will(returnValue(issueObject));
        mockPermissionManager.expects(once()).method("hasPermission")
                .with(eq(Permissions.VIEW_VERSION_CONTROL), same(issueObject), new IsAnything())
                .will(returnValue(true));

        svnLogEntry = new SVNLogEntry(Collections.EMPTY_MAP, 1L, "dchui", new Date(), "TST-1");
        mockSvnMgr.expects(once()).method("getLogEntry").with(eq(1L)).will(returnValue(svnLogEntry));
        mockMultipleSubversionRepositoryManager.expects(once()).method("getRepository").with(eq(1L)).will(returnValue(svnMgr));

        Map entriesMap;
        List entries;

        RevisionIndexer revisionIndexer = getRevisionIndexer();
        entriesMap = revisionIndexer.getLogEntriesByVersion((Version) new Mock(Version.class).proxy(), null, 0, Integer.MAX_VALUE);
        entries = (List) entriesMap.get(1L);

        assertEquals(1, entriesMap.size()); /* There should be 1 entry because it matches repository 1 */
        assertEquals(1, entries.size()); /* There should be 1 entry because it matches revision 1 */
        assertSame(svnLogEntry, entries.get(0));
    }

    public void testAddRepository() throws IOException, IndexException {
        /* We'll start off with an empty index */
        IndexWriter indexWriter;
        IndexReader indexReader;
        RevisionIndexer revisionIndexer;

        indexWriter = new DefaultLuceneIndexAccessor().getIndexWriter(getIndexPath(), true, RevisionIndexer.ANALYZER);
        indexWriter.close();

        /* Create a SubversionManager which will contain one entry to be indexed */
        final List<SVNLogEntry> entries = ImmutableList.of(new SVNLogEntry(Collections.EMPTY_MAP, 0, "dchui", new Date(), "TST-1"));
        mockSvnMgr.expects(atLeastOnce()).method("getId").withNoArguments().will(returnValue(1L));
        mockSvnMgr.expects(atLeastOnce()).method("isActive").withNoArguments().will(returnValue(true));
        mockSvnMgr.expects(atLeastOnce()).method("getLogEntries").with(eq(-1L), ANYTHING).will(callHandlerWithLogEntries(entries));

        mockMultipleSubversionRepositoryManager.reset();
        mockMultipleSubversionRepositoryManager.expects(exactly(2)).method("getRepositoryList").withNoArguments().will(
                onConsecutiveCalls(
                        returnValue(Collections.EMPTY_LIST),
                        returnValue(EasyList.build(svnMgr))
                )
        );

        revisionIndexer = new RevisionIndexer(multipleSubversionRepositoryManager,
                versionManager, issueManager, permissionManager, changeHistoryManager, indexPathManager) {
            protected boolean isKeyInString(SVNLogEntry logEntry) {
                return true;
            }

            protected String getProjectKeyFromIssueKey(String issueKey) {
                return "TST";
            }

            protected List<String> getIssueKeysFromString(SVNLogEntry logEntry) {
                return EasyList.build("TST-1");
            }
        };
        revisionIndexer.addRepository(svnMgr.getId());

        indexReader = new DefaultLuceneIndexAccessor().getIndexReader(getIndexPath());

        final TermDocs termDocs = indexReader.termDocs(new Term(RevisionIndexer.FIELD_REPOSITORY, Long.toString(1L)));

        assertTrue(termDocs.next()); /* Contains one entry only */
        assertFalse(termDocs.next()); /* Should return false if there is only one entry */

        indexReader.close();
    }

    public void testRemoveEntries() throws IOException, IndexException, SVNException {
        /* We'll start off with an empty index */
        IndexWriter indexWriter;
        IndexReader indexReader;
        RevisionIndexer revisionIndexer;

        indexWriter = new DefaultLuceneIndexAccessor().getIndexWriter(getIndexPath(), true, RevisionIndexer.ANALYZER);
        indexWriter.close();

        /* Create a SubversionManager which will contain one entry to be indexed */
        final List<SVNLogEntry> entries = ImmutableList.of(new SVNLogEntry(Collections.EMPTY_MAP, 0, "dchui", new Date(), "TST-1"));
        mockSvnMgr.expects(atMostOnce()).method("getRoot").withNoArguments().will(returnValue(System.getProperty("svn.root")));
        mockSvnMgr.expects(atLeastOnce()).method("getId").withNoArguments().will(returnValue(1L));
        mockSvnMgr.expects(atLeastOnce()).method("isActive").withNoArguments().will(returnValue(true));
        mockSvnMgr.expects(atLeastOnce()).method("getLogEntries").with(eq(-1L), ANYTHING).will(callHandlerWithLogEntries(entries));

        mockMultipleSubversionRepositoryManager.reset();
        mockMultipleSubversionRepositoryManager.expects(atLeastOnce()).method("getRepositoryList").withNoArguments().will(
                returnValue(EasyList.build(svnMgr))
        );

        revisionIndexer = new RevisionIndexer(multipleSubversionRepositoryManager,
                versionManager, issueManager, permissionManager, changeHistoryManager, indexPathManager) {
            protected boolean isKeyInString(SVNLogEntry logEntry) {
                return true;
            }

            protected String getProjectKeyFromIssueKey(String issueKey) {
                return "TST";
            }

            protected List<String> getIssueKeysFromString(SVNLogEntry logEntry) {
                return EasyList.build("TST-1");
            }
        };
        revisionIndexer.updateIndex();

        indexReader = new DefaultLuceneIndexAccessor().getIndexReader(getIndexPath());
        assertEquals(1, indexReader.numDocs()); /* Contains one entry only. This is what we are going to change */
        indexReader.close();

        revisionIndexer.removeEntries(svnMgr.getId());
        indexReader = new DefaultLuceneIndexAccessor().getIndexReader(getIndexPath());
        assertEquals(0, indexReader.numDocs()); /* There should be no matches */
        indexReader.close();
    }

    private CustomStub callHandlerWithLogEntries(final List<SVNLogEntry> entries) {
        return new CustomStub("call log entries") {
            @Override
            public Object invoke(final Invocation invocation) throws Throwable {
                final SvnEntryHandler param = (SvnEntryHandler) invocation.parameterValues.get(1);
                for (SVNLogEntry entry : entries) {
                    param.handle(entry);
                }
                return null;
            }
        };
    }
}
