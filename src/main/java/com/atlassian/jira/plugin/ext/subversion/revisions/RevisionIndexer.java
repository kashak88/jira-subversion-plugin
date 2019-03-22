/*
 * Created by IntelliJ IDEA.
 * User: Mike
 * Date: Oct 1, 2004
 * Time: 4:58:40 PM
 */
package com.atlassian.jira.plugin.ext.subversion.revisions;

import com.atlassian.jira.InfrastructureException;
import com.atlassian.jira.config.util.IndexPathManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.changehistory.ChangeHistoryManager;
import com.atlassian.jira.issue.index.IndexException;
import com.atlassian.jira.plugin.ext.subversion.MultipleSubversionRepositoryManager;
import com.atlassian.jira.plugin.ext.subversion.SubversionManager;
import com.atlassian.jira.plugin.ext.subversion.SvnEntryHandler;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.project.version.VersionManager;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.JiraKeyUtils;
import com.google.common.collect.Maps;
import com.opensymphony.util.TextUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.DateField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.SimpleFSLockFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public class RevisionIndexer {
    private final static Logger log = LoggerFactory.getLogger(RevisionIndexer.class);
    private static final Long NOT_INDEXED = -1L;

    static final String REVISIONS_INDEX_DIRECTORY = "atlassian-subversion-revisions";

    // These are names of the fields in the Lucene documents that contain revision info.
    public static final String FIELD_REVISIONNUMBER = "revision";
    public static final Term START_REVISION = new Term(FIELD_REVISIONNUMBER, "");
    public static final String FIELD_MESSAGE = "message";
    public static final String FIELD_AUTHOR = "author";
    public static final String FIELD_DATE = "date";
    public static final String FIELD_ISSUEKEY = "key";
    public static final String FIELD_PROJECTKEY = "project";
    public static final String FIELD_REPOSITORY = "repository";

    public static final StandardAnalyzer ANALYZER = new StandardAnalyzer(org.apache.lucene.util.Version.LUCENE_30);

    public static final int MAX_REVISIONS = 100;

    private final MultipleSubversionRepositoryManager multipleSubversionRepositoryManager;
    private final VersionManager versionManager;
    private final IssueManager issueManager;
    private final PermissionManager permissionManager;
    private final ChangeHistoryManager changeHistoryManager;
    private final IndexPathManager indexPathManager;
    private ConcurrentMap<Long, Long> latestIndexedRevisionTbl;
    private LuceneIndexAccessor indexAccessor;

    public RevisionIndexer(MultipleSubversionRepositoryManager multipleSubversionRepositoryManager, VersionManager versionManager, IssueManager issueManager, PermissionManager permissionManager, ChangeHistoryManager changeHistoryManager, IndexPathManager indexPathManager) {
        this(multipleSubversionRepositoryManager, versionManager, issueManager, permissionManager, changeHistoryManager, new DefaultLuceneIndexAccessor(), indexPathManager);
    }


    RevisionIndexer(MultipleSubversionRepositoryManager multipleSubversionRepositoryManager, VersionManager versionManager, IssueManager issueManager, PermissionManager permissionManager, ChangeHistoryManager changeHistoryManager, LuceneIndexAccessor accessor, IndexPathManager indexPathManager) {
        this.multipleSubversionRepositoryManager = multipleSubversionRepositoryManager;
        this.versionManager = versionManager;
        this.issueManager = issueManager;
        this.permissionManager = permissionManager;
        this.changeHistoryManager = changeHistoryManager;
        this.indexAccessor = accessor;
        this.indexPathManager = indexPathManager;
        initializeLatestIndexedRevisionCache();
    }

    public void start() {
        try {
            createIndexIfNeeded();
        } catch (Exception e) {
            log.error("Error installing the revision index service.", e);
            throw new InfrastructureException("Error installing the revision index service.", e);
        }
    }

    /**
     * Looks for the revision index directory and creates it if it does not already exists.
     *
     * @return Return <tt>true</tt> if the index directory is usable or created; <tt>false</tt> otherwise.
     */
    private boolean createIndexIfNeeded() {
        if (log.isDebugEnabled())
            log.debug("RevisionIndexer.createIndexIfNeeded()");

        boolean indexExists = indexDirectoryExists();
        if (getIndexPath() != null && !indexExists) {
            try {
                indexAccessor.getIndexWriter(getIndexPath(), true, ANALYZER).close();
                initializeLatestIndexedRevisionCache();
                return true;
            } catch (IOException ioe) {
                log.error("There's a performing IO on the index.", ioe);
                return false;
            }
        } else {
            return indexExists;
        }
    }

    private void initializeLatestIndexedRevisionCache() {
        Collection<SubversionManager> repositories = multipleSubversionRepositoryManager.getRepositoryList();

        latestIndexedRevisionTbl = Maps.newConcurrentMap();

        for (SubversionManager currentRepo : repositories)
            initializeLatestIndexedRevisionCache(currentRepo.getId());

        if (log.isDebugEnabled())
            log.debug("Number of repositories: " + repositories.size());
    }

    private void initializeLatestIndexedRevisionCache(long repositoryId) {
        latestIndexedRevisionTbl.put(repositoryId, NOT_INDEXED);
    }

    private boolean indexDirectoryExists() {
        try {
            // check if the directory exists
            File file = new File(getIndexPath());

            return file.exists();
        } catch (Exception e) {
            return false;
        }
    }

    public String getIndexPath() {
        String indexPath = null;
        String rootIndexPath = indexPathManager.getPluginIndexRootPath();
        if (rootIndexPath != null) {
            indexPath = rootIndexPath + System.getProperty("file.separator") + REVISIONS_INDEX_DIRECTORY;
        } else {
            log.warn("At the moment the root index path of jira is not set, so we can not form an index path for the subversion plugin.");
        }

        return indexPath;
    }

    /**
     * This method updates the index, creating it if it does not already exist.
     * TODO: this monster really needs to be broken down - weed out the loop control
     *
     * @throws IndexException if there is some problem in the indexing subsystem meaning indexes cannot be updated.
     */
    public void updateIndex() throws IndexException, IOException {
        if (createIndexIfNeeded()) {
            Collection<SubversionManager> repositories = multipleSubversionRepositoryManager.getRepositoryList();

            // temp log comment
            if (log.isDebugEnabled())
                log.debug("Number of repositories: " + repositories.size());

            for (SubversionManager subversionManager : repositories) {
                try {
                    // if the repository isn't active, try activating it. if it still not accessible, skip it
                    if (!subversionManager.isActive()) {
                        subversionManager.activate();

                        if (!subversionManager.isActive()) {
                            continue;
                        }
                    }

                    final long repoId = subversionManager.getId();
                    final long latestIndexedRevision;

                    final Long tmpLatestIndexedRevision = getLatestIndexedRevision(repoId);
                    if (tmpLatestIndexedRevision != null) {
                        if (tmpLatestIndexedRevision < 0) {
                            latestIndexedRevision = updateLastRevisionIndexed(repoId);
                        } else {
                            latestIndexedRevision = tmpLatestIndexedRevision;
                        }
                    } else {
                        // no latestIndexedRevision, no need to update? This probably means
                        // that the repository have been removed from the file system
                        log.warn("Did not update index because null value in hash table for " + repoId);
                        continue;
                    }

                    if (log.isDebugEnabled()) {
                        log.debug("Updating revision index for repository=" + repoId);
                    }

                    if (log.isDebugEnabled()) {
                        log.debug("Latest indexed revision for repository=" + repoId + " is : " + latestIndexedRevision);
                    }

                    final IndexWriter writer = indexAccessor.getIndexWriter(getIndexPath(), false, ANALYZER);

                    try {

                        final IndexReader reader = indexAccessor.getIndexReader(getIndexPath());

                        try {
                            subversionManager.getLogEntries(latestIndexedRevision, new SvnEntryHandler() {
                                        @Override
                                        public void handle(final SVNLogEntry logEntry) {
                                            if (TextUtils.stringSet(logEntry.getMessage()) && isKeyInString(logEntry)) {
                                                try {
                                                    if (!hasDocument(repoId, logEntry.getRevision(), reader)) {
                                                        Document doc = getDocument(repoId, logEntry);
                                                        if (log.isDebugEnabled()) {
                                                            log.debug("Indexing repository=" + repoId + ", revision: " + logEntry.getRevision());
                                                        }
                                                        writer.addDocument(doc);
                                                    }
                                                } catch (IOException e) {
                                                    throw new RuntimeException(e);
                                                }
                                            }
                                            if (logEntry.getRevision() > latestIndexedRevision) {
                                                // update the in-memory cache SVN-71
                                                latestIndexedRevisionTbl.put(repoId, logEntry.getRevision());
                                            }
                                        }
                                    }
                            );
                        } finally {
                            reader.close();
                        }
                    } finally {
                        writer.close();
                    }
                } catch (IOException e) {
                    log.warn("Unable to index repository '" + subversionManager.getDisplayName() + "'", e);
                } catch (RuntimeException e) {
                    log.warn("Unable to index repository '" + subversionManager.getDisplayName() + "'", e);
                }
            }  // while
        }
    }

    protected boolean isKeyInString(SVNLogEntry logEntry) {
        final String logMessageUpperCase = StringUtils.upperCase(logEntry.getMessage());
        return JiraKeyUtils.isKeyInString(logMessageUpperCase);
    }

    protected Long getLatestIndexedRevision(long repoId) {
        return latestIndexedRevisionTbl.get(new Long(repoId));
    }

    /**
     * Work out whether a given change, for the specified repository, is already in the index or not.
     */
    private boolean hasDocument(long repoId, long revisionNumber, IndexReader reader) throws IOException {
        IndexSearcher searcher = new IndexSearcher(reader);
        try {
            TermQuery repoQuery = new TermQuery(new Term(FIELD_REPOSITORY, Long.toString(repoId)));
            TermQuery revQuery = new TermQuery(new Term(FIELD_REVISIONNUMBER, Long.toString(revisionNumber)));
            BooleanQuery repoAndRevQuery = new BooleanQuery();

            repoAndRevQuery.add(repoQuery, BooleanClause.Occur.MUST);
            repoAndRevQuery.add(revQuery, BooleanClause.Occur.MUST);

            TopDocs hits = searcher.search(repoAndRevQuery, MAX_REVISIONS);

            if (hits.totalHits == 1) {
                return true;
            } else if (hits.totalHits == 0) {
                return false;
            } else {
                log.error("Found MORE than one document for revision: " + revisionNumber + ", repository=" + repoId);
                return true;
            }
        } finally {
            searcher.close();
        }
    }


    private long updateLastRevisionIndexed(long repoId) throws IndexException, IOException {
        if (log.isDebugEnabled()) {
            log.debug("Updating last revision indexed.");
        }

        // find all log entries that have already been indexed for the specified repository
        // (i.e. all logs that have been associated with issues in JIRA)
        long latestIndexedRevision = latestIndexedRevisionTbl.get(repoId);

        String indexPath = getIndexPath();
        final IndexReader reader;
        try {
            reader = IndexReader.open(FSDirectory.open(new File(indexPath), new SimpleFSLockFactory()));
        } catch (IOException e) {
            log.error("Problem with path " + indexPath + ": " + e.getMessage(), e);
            throw new IndexException("Problem with path " + indexPath + ": " + e.getMessage(), e);
        }
        IndexSearcher searcher = new IndexSearcher(reader);

        try {
            TopDocs hits = searcher.search(new TermQuery(new Term(FIELD_REPOSITORY, Long.toString(repoId))), MAX_REVISIONS, new Sort(new SortField(FIELD_REVISIONNUMBER, SortField.LONG, true)));
            if (hits.totalHits > 0) {
                Document doc = searcher.doc(hits.scoreDocs[0].doc);
                final long revision = Long.parseLong(doc.get(FIELD_REVISIONNUMBER));
                if (revision > latestIndexedRevision) {
                    latestIndexedRevision = revision;
                }
            }
            log.debug("latestIndRev for " + repoId + " = " + latestIndexedRevision);
            latestIndexedRevisionTbl.put(repoId, latestIndexedRevision);
        } finally {
            reader.close();
        }

        return latestIndexedRevision;
    }

    /**
     * Creates a new Lucene document for the supplied log entry. This method is used when indexing
     * revisions, not during retrieval.
     *
     * @param repoId   ID of the repository that contains the revision
     * @param logEntry The subversion log entry that is about to be indexed
     * @return A Lucene document object that is ready to be added to an index
     */
    protected Document getDocument(long repoId, SVNLogEntry logEntry) {
        Document doc = new Document();

        // revision information
        doc.add(new Field(FIELD_MESSAGE, logEntry.getMessage(), Field.Store.YES, Field.Index.NOT_ANALYZED));

        if (logEntry.getAuthor() != null) {
            doc.add(new Field(FIELD_AUTHOR, logEntry.getAuthor(), Field.Store.YES, Field.Index.NOT_ANALYZED));
        }

        doc.add(new Field(FIELD_REPOSITORY, Long.toString(repoId), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(FIELD_REVISIONNUMBER, Long.toString(logEntry.getRevision()), Field.Store.YES, Field.Index.NOT_ANALYZED));

        if (logEntry.getDate() != null) {
            doc.add(new Field(FIELD_DATE, DateField.dateToString(logEntry.getDate()), Field.Store.YES, Field.Index.NOT_ANALYZED));
        }

        // relevant issue keys
        List<String> keys = getIssueKeysFromString(logEntry);

        // Relevant project keys. Used to avoid adding duplicate projects.
        Map<String, String> projects = new HashMap<String, String>();

        for (String issueKey : keys) {
            doc.add(new Field(FIELD_ISSUEKEY, issueKey, Field.Store.YES, Field.Index.NOT_ANALYZED));
            String projectKey = getProjectKeyFromIssueKey(issueKey);
            if (!projects.containsKey(projectKey)) {
                projects.put(projectKey, projectKey);
                doc.add(new Field(FIELD_PROJECTKEY, projectKey, Field.Store.YES, Field.Index.NOT_ANALYZED));
            }
        }

        return doc;
    }

    protected String getProjectKeyFromIssueKey(String issueKey) {
        final String issueKeyUpperCase = StringUtils.upperCase(issueKey);
        return JiraKeyUtils.getFastProjectKeyFromIssueKey(issueKeyUpperCase);
    }

    protected List<String> getIssueKeysFromString(SVNLogEntry logEntry) {
        final String logMessageUpperCase = StringUtils.upperCase(logEntry.getMessage());
        return JiraKeyUtils.getIssueKeysFromString(logMessageUpperCase);
    }

    public Map<Long, List<SVNLogEntry>> getLogEntriesByRepository(Issue issue) throws IndexException, IOException {
        return getLogEntriesByRepository(issue, 0, MAX_REVISIONS, true);
    }

    /**
     * Gets the commits relevant to the specified issue from all the configured repositories.
     *
     * @param issue      The issue to get entries for.
     * @param startIndex For paging &mdash; The index of the entry that is the first result in the page desired.
     * @param pageSize   For paging &mdash; The size of the page.
     * @return A {@link java.util.Map} of {@com.atlassian.jira.plugin.ext.subversion.SubversionManager} IDs to the commits in them
     * that relate to the issue.
     * @throws IndexException Thrown if there's a getting a reader to the index.
     * @throws IOException    Thrown if there's a problem reading the index.
     */
    public Map<Long, List<SVNLogEntry>> getLogEntriesByRepository(Issue issue, int startIndex, int pageSize, boolean ascending) throws IndexException, IOException {
        if (log.isDebugEnabled())
            log.debug("Retrieving revisions for : " + issue.getKey());


        if (!indexDirectoryExists()) {
            log.warn("The indexes for the subversion plugin have not yet been created.");
            return null;
        } else {
            final IndexReader reader = indexAccessor.getIndexReader(getIndexPath());
            IndexSearcher searcher = new IndexSearcher(reader);

            try {
                TopDocs hits = searcher.search(createQueryByIssueKey(issue), MAX_REVISIONS, new Sort(new SortField(FIELD_DATE, SortField.STRING, !ascending)));
                Map<Long, List<SVNLogEntry>> logEntries = new LinkedHashMap<Long, List<SVNLogEntry>>(hits.totalHits);

                int endIndex = startIndex + pageSize;

                // SVN-370 - Prevent ArrayIndexOutOfBoundsException when more than 100 commits (which is MAX_REVISIONS) are to be shown
                for (int i = 0; i < Math.min(hits.totalHits, MAX_REVISIONS); i++) {
                    if (i < startIndex || i >= endIndex)
                        continue;

                    Document doc = searcher.doc(hits.scoreDocs[i].doc);
                    long repositoryId = Long.parseLong(doc.get(FIELD_REPOSITORY));//repositoryId is UUID + location
                    SubversionManager manager = multipleSubversionRepositoryManager.getRepository(repositoryId);
                    long revision = Long.parseLong(doc.get(FIELD_REVISIONNUMBER));
                    SVNLogEntry logEntry = manager.getLogEntry(revision);
                    if (logEntry == null) {
                        log.error("Could not find log message for revision: " + Long.parseLong(doc.get(FIELD_REVISIONNUMBER)));
                    } else {
                        // Look for list of map entries for repository
                        List<SVNLogEntry> entries = logEntries.get(repositoryId);
                        if (entries == null) {
                            entries = new ArrayList<SVNLogEntry>();
                            logEntries.put(repositoryId, entries);
                        }
                        entries.add(logEntry);
                    }
                }

                return logEntries;
            } finally {
                searcher.close();
                reader.close();
            }
        }
    }

    /**
     * Gets the commits relevant to the specified project.
     *
     * @param projectKey The project key.
     * @param user       The requesting user.
     * @param startIndex For paging &mdash; The index of the entry that is the first result in the page desired.
     * @param pageSize   For paging &mdash; The size of the page.
     * @return A {@link java.util.Map} of {@com.atlassian.jira.plugin.ext.subversion.SubversionManager} IDs to the commits in them
     * that relate to the project.
     * @throws IndexException Thrown if there's a getting a reader to the index.
     * @throws IOException    Thrown if there's a problem reading the index.
     */
    public Map<Long, List<SVNLogEntry>> getLogEntriesByProject(String projectKey, ApplicationUser user, int startIndex, int pageSize) throws IndexException, IOException {
        if (!indexDirectoryExists()) {
            log.warn("getLogEntriesByProject() The indexes for the subversion plugin have not yet been created.");
            return null;
        } else {

            // Set up and perform a search for all documents having the supplied projectKey,
            // sorted in descending date order
            TermQuery query = new TermQuery(new Term(FIELD_PROJECTKEY, projectKey));

            Map<Long, List<SVNLogEntry>> logEntries;
            final IndexReader reader = indexAccessor.getIndexReader(getIndexPath());
            IndexSearcher searcher = new IndexSearcher(reader);

            try {
                TopDocs hits = searcher.search(query, new ProjectRevisionFilter(issueManager, permissionManager, user, projectKey), MAX_REVISIONS, new Sort(new SortField(FIELD_DATE, SortField.LONG, true)));

                if (hits == null) {
                    log.info("getLogEntriesByProject() No matches -- returning null.");
                    return null;
                }
                // Build the result map
                logEntries = new LinkedHashMap<Long, List<SVNLogEntry>>();
                int endIndex = startIndex + pageSize;

                for (int i = 0, j = Math.min(hits.totalHits, MAX_REVISIONS); i < j; ++i) {
                    if (i < startIndex || i >= endIndex)
                        continue;

                    Document doc = searcher.doc(hits.scoreDocs[i].doc);

                    long repositoryId = Long.parseLong(doc.get(FIELD_REPOSITORY));//repositoryId is UUID + location
                    SubversionManager manager = multipleSubversionRepositoryManager.getRepository(repositoryId);
                    long revision = Long.parseLong(doc.get(FIELD_REVISIONNUMBER));
                    SVNLogEntry logEntry = manager.getLogEntry(revision);
                    if (logEntry == null) {
                        log.error("getLogEntriesByProject() Could not find log message for revision: " + revision);
                        continue;
                    }
                    // Look up the list of map entries for this repository. Create one if needed
                    List<SVNLogEntry> entries = logEntries.get(repositoryId);
                    if (entries == null) {
                        entries = new ArrayList<SVNLogEntry>();
                        logEntries.put(repositoryId, entries);
                    }

                    // Add this entry
                    entries.add(logEntry);
                }
            } finally {
                searcher.close();
                reader.close();
            }

            return logEntries;
        }
    }


    /**
     * Gets all commits for issues related to version specified from all configured repositories.
     *
     * @param version    The version to get entries for. May not be <tt>null</tt>.
     * @param user       The requesting user.
     * @param startIndex For paging &mdash; The index of the entry that is the first result in the page desired.
     * @param pageSize   For paging &mdash; The size of the page.
     * @return A {@link java.util.Map} of {@com.atlassian.jira.plugin.ext.subversion.SubversionManager} IDs to the commits in them
     * that relate to the version.
     * @throws IndexException Thrown if there's a getting a reader to the index.
     * @throws IOException    Thrown if there's a problem reading the index.
     */
    public Map<Long, List<SVNLogEntry>> getLogEntriesByVersion(Version version, ApplicationUser user, int startIndex, int pageSize) throws IndexException, IOException {
        if (!indexDirectoryExists()) {
            log.warn("getLogEntriesByVersion() The indexes for the subversion plugin have not yet been created.");
            return null;
        }

        // Find all isuses affected by and fixed by any of the versions:
        Collection<Issue> issues = new HashSet();

        issues.addAll(versionManager.getIssuesWithFixVersion(version));
        issues.addAll(versionManager.getIssuesWithAffectsVersion(version));

        // Construct a query with all the issue keys. Make sure to increase the maximum number of clauses if needed.
        int maxClauses = BooleanQuery.getMaxClauseCount();
        if (issues.size() > maxClauses)
            BooleanQuery.setMaxClauseCount(issues.size());

        BooleanQuery query = new BooleanQuery();
        Set<String> permittedIssueKeys = new HashSet();

        for (Issue issue : issues) {
            String key = issue.getKey();
            Issue theIssue = issueManager.getIssueObject(key);

            if (permissionManager.hasPermission(Permissions.VIEW_VERSION_CONTROL, theIssue, user)) {
                TermQuery termQuery = new TermQuery(new Term(FIELD_ISSUEKEY, key));
                query.add(termQuery, BooleanClause.Occur.SHOULD);
                permittedIssueKeys.add(key);
            }
        }

        final IndexReader reader = indexAccessor.getIndexReader(getIndexPath());
        IndexSearcher searcher = new IndexSearcher(reader);
        Map<Long, List<SVNLogEntry>> logEntries;

        try {
            // Run the query and sort by date in descending order
            TopDocs hits = searcher.search(query, new PermittedIssuesRevisionFilter(issueManager, permissionManager, user, permittedIssueKeys), MAX_REVISIONS, new Sort(new SortField(FIELD_DATE, SortField.LONG, true)));

            if (hits == null) {
                log.info("getLogEntriesByVersion() No matches -- returning null.");
                return null;
            }

            logEntries = new LinkedHashMap<Long, List<SVNLogEntry>>();
            int endDocIndex = startIndex + pageSize;

            for (int i = 0, j = Math.min(hits.totalHits, MAX_REVISIONS); i < j; ++i) {
                if (i < startIndex || i >= endDocIndex)
                    continue;

                Document doc = searcher.doc(hits.scoreDocs[i].doc);
                long repositoryId = Long.parseLong(doc.get(FIELD_REPOSITORY));//repositoryId is UUID + location
                SubversionManager manager = multipleSubversionRepositoryManager.getRepository(repositoryId);
                long revision = Long.parseLong(doc.get(FIELD_REVISIONNUMBER));

                SVNLogEntry logEntry = manager.getLogEntry(revision);
                if (logEntry == null) {
                    log.error("getLogEntriesByVersion() Could not find log message for revision: " + Long.parseLong(doc.get(FIELD_REVISIONNUMBER)));
                }
                // Add the entry to the list of map entries for the repository. Create a new list if needed
                List<SVNLogEntry> entries = logEntries.get(repositoryId);
                if (entries == null) {
                    entries = new ArrayList<SVNLogEntry>();
                    logEntries.put(repositoryId, entries);
                }
                entries.add(logEntry);
            }
        } finally {
            searcher.close();
            reader.close();
            BooleanQuery.setMaxClauseCount(maxClauses);
        }

        return logEntries;
    }

    public void addRepository(long repositoryId) {
        if (log.isDebugEnabled()) {
            log.debug("Adding repository with id: " + repositoryId);
        }

        initializeLatestIndexedRevisionCache(repositoryId);
        try {
            updateIndex();
        } catch (Exception e) {
            throw new InfrastructureException("Could not index repository", e);
        }
    }

    public void removeEntries(final long repositoryId) throws IOException, IndexException, SVNException {
        if (log.isDebugEnabled()) {
            log.debug("Deleteing revisions for repositoryId: " + repositoryId);
        }

        if (!indexDirectoryExists()) {
            log.warn("The indexes for the subversion plugin have not yet been created.");
        } else {
            IndexWriter writer = null;

            try {
                writer = indexAccessor.getIndexWriter(getIndexPath(), false, ANALYZER);

                writer.deleteDocuments(new Term(FIELD_REPOSITORY, Long.toString(repositoryId)));
                initializeLatestIndexedRevisionCache(repositoryId);
            } catch (IOException ie) {
                if (log.isErrorEnabled())
                    log.error("Unable to open index. " +
                            "Perhaps the index is corrupted. It might be possible to fix the problem " +
                            "by removing the index directory (" + getIndexPath() + ")", ie);

                throw ie; /* Rethrow for normal error handling? SVN-200 */
            } finally {
                if (null != writer) {
                    try {
                        writer.close();
                    } catch (IOException ioe) {
                        if (log.isWarnEnabled())
                            log.warn("Unable to close index.", ioe);
                    }
                }

            }
        }
    }

    /**
     * Returns the query that matches the key of the passed issue and
     * any previous keys this issue had if it has moved between
     * projects previously.
     */
    protected Query createQueryByIssueKey(Issue issue) {
        BooleanQuery query = new BooleanQuery();

        // add current key
        query.add(new TermQuery(new Term(FIELD_ISSUEKEY, issue.getKey())), BooleanClause.Occur.SHOULD);

        // add all previous keys
        Collection<String> previousIssueKeys = changeHistoryManager.getPreviousIssueKeys(issue.getId());
        for (String previousIssueKey : previousIssueKeys) {
            TermQuery termQuery = new TermQuery(new Term(FIELD_ISSUEKEY, previousIssueKey));
            query.add(termQuery, BooleanClause.Occur.SHOULD);
        }

        return query;
    }
}
