package com.atlassian.jira.plugin.ext.subversion;

import com.atlassian.beehive.ClusterLockService;
import com.atlassian.cache.CacheManager;
import com.atlassian.cache.CacheSettingsBuilder;
import com.atlassian.cache.CachedReference;
import com.atlassian.cache.Supplier;
import com.atlassian.jira.InfrastructureException;
import com.atlassian.jira.cluster.ClusterMessagingService;
import com.atlassian.jira.config.util.IndexPathManager;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.changehistory.ChangeHistoryManager;
import com.atlassian.jira.plugin.ext.subversion.messages.DefaultIndexMessageService;
import com.atlassian.jira.plugin.ext.subversion.messages.IndexMessageService;
import com.atlassian.jira.plugin.ext.subversion.revisions.RevisionIndexer;
import com.atlassian.jira.project.version.VersionManager;
import com.atlassian.jira.propertyset.JiraPropertySetFactory;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.opensymphony.module.propertyset.PropertySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;


/**
 * This is a wrapper class for many SubversionManagers.
 * Configured via {@link SvnPropertiesLoader#PROPERTIES_FILE_NAME}
 *
 * @author Dylan Etkin
 * @see {@link com.atlassian.jira.plugin.ext.subversion.SubversionManager}
 */
public class MultipleSubversionRepositoryManagerImpl implements MultipleSubversionRepositoryManager, LifecycleAware, InitializingBean, DisposableBean {
    private final static Logger log = LoggerFactory.getLogger(MultipleSubversionRepositoryManagerImpl.class);

    protected static final String REPOSITORY_INDEX_LOCK = MultipleSubversionRepositoryManagerImpl.class.getName() + ".repoIndexLock";

    public static final String APP_PROPERTY_PREFIX = "jira.plugins.subversion";

    public static final String REPO_PROPERTY = "jira.plugins.subversion.repo";

    public static final String LAST_REPO_ID = "last.repo.id";

    public static final long FIRST_REPO_ID = 1;

    private final VersionManager versionManager;

    private final JiraPropertySetFactory jiraPropertySetFactory;

    private final ClusterLockService clusterLockService;

    private final CachedReference<Map<Long, SubversionManager>> cachedSvnManagers;

    private final ClusterMessagingService clusterMessagingService;

    private final IssueManager issueManager;

    private final PermissionManager permissionManager;

    private final ChangeHistoryManager changeHistoryManager;

    private final IndexPathManager indexPathManager;

    private IndexMessageService indexMessageService;

    private RevisionIndexer revisionIndexer;

    public MultipleSubversionRepositoryManagerImpl(
            VersionManager versionManager,
            IssueManager issueManager,
            PermissionManager permissionManager,
            ChangeHistoryManager changeHistoryManager,
            JiraPropertySetFactory jiraPropertySetFactory,
            IndexPathManager indexPathManager,
            CacheManager cacheManager,
            ClusterLockService clusterLockService,
            ClusterMessagingService clusterMessagingService) {
        this.jiraPropertySetFactory = jiraPropertySetFactory;
        this.clusterLockService = clusterLockService;
        this.clusterMessagingService = clusterMessagingService;
        this.versionManager = versionManager;
        this.issueManager = issueManager;
        this.permissionManager = permissionManager;
        this.changeHistoryManager = changeHistoryManager;
        this.indexPathManager = indexPathManager;

        // Initialize the SVN tools
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        FSRepositoryFactory.setup();

        cachedSvnManagers = cacheManager.getCachedReference(MultipleSubversionRepositoryManagerImpl.class, "cachedSvnManagers",
                new SvnManagerCacheSupplier(),
                new CacheSettingsBuilder()
                        .expireAfterAccess(30, TimeUnit.MINUTES)
                        .build());

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // create revision indexer once we know we have succeed initializing our repositories
        revisionIndexer = new RevisionIndexer(this, versionManager, issueManager, permissionManager, changeHistoryManager, indexPathManager);
        indexMessageService = new DefaultIndexMessageService(clusterMessagingService, revisionIndexer, cachedSvnManagers);
        indexMessageService.registerListeners();
    }

    @Override
    public void onStart() {
        try {
            if (isIndexingRevisions()) {
                startRevisionIndexer();
            }
        } catch (InfrastructureException ie) {
            /* Log error, don't throw. Otherwise, we get SVN-234 */
            log.error("Error starting " + getClass(), ie);
        }
    }

    @Override
    public void destroy() throws Exception {
        indexMessageService.unregisterListeners();
    }

    private class SvnManagerCacheSupplier implements Supplier<Map<Long, SubversionManager>> {
        @Override
        public Map<Long, SubversionManager> get() {
            return loadManagersFromJiraProperties();
        }
    }

    /**
     * The Subversion configuration properties are stored in the application properties.  It's not the best place to
     * store collections of information, like multiple repositories, but it will work.  Keys for the properties look
     * like:
     * <p/>
     * <tt>jira.plugins.subversion.&lt;repoId&gt;;&lt;property name&gt;</tt>
     * <p/>
     * Using this scheme we can get all the properties and put them into buckets corresponding to the <tt>repoId</tt>.
     * Then when we have all the properties we can go about building the {@link com.atlassian.jira.plugin.ext.subversion.SubversionProperties}
     * objects and creating our {@link com.atlassian.jira.plugin.ext.subversion.SubversionManager}s.
     *
     * @return A {@link java.util.Map} of {@link com.atlassian.jira.plugin.ext.subversion.SubversionManager} IDs to the {@link com.atlassian.jira.plugin.ext.subversion.SubversionManager}.
     * loaded from JIRA's application properties.
     */
    private Map<Long, SubversionManager> loadManagersFromJiraProperties() {
        final PropertySet propertySet = jiraPropertySetFactory.buildNoncachingPropertySet(APP_PROPERTY_PREFIX);
        final long lastRepoId = propertySet.getLong(LAST_REPO_ID);

        // create the SubversionManagers
        final Map<Long, SubversionManager> managers = Maps.newHashMap();
        for (long i = FIRST_REPO_ID; i <= lastRepoId; i++) {
            final SubversionManager manager = createManagerFromPropertySet(i, jiraPropertySetFactory.buildCachingPropertySet(REPO_PROPERTY, i, true));
            if (manager != null) {
                managers.put(i, manager);
            }
        }
        return managers;
    }

    private SubversionManager createManagerFromPropertySet(long index, PropertySet properties) {
        try {
            if (properties.getKeys().isEmpty())
                return null;

            return new SubversionManagerImpl(index, properties);
        } catch (IllegalArgumentException e) {
            log.error("Error creating SubversionManager " + index + ". Probably was missing a required field (e.g., repository name or root). Skipping it.", e);
            return null;
        }
    }

    public SubversionManager createRepository(SvnProperties properties) {
        final Lock lock = clusterLockService.getLockForName(REPOSITORY_INDEX_LOCK);
        final PropertySet propertySet = jiraPropertySetFactory.buildNoncachingPropertySet(APP_PROPERTY_PREFIX);

        lock.lock();

        long repositoryId;
        try {
            repositoryId = propertySet.getLong(LAST_REPO_ID) + 1;
            propertySet.setLong(LAST_REPO_ID, repositoryId);
        } finally {
            lock.unlock();
        }

        final SubversionManager subversionManager = createSubversionManager(properties, repositoryId);
        cachedSvnManagers.reset();

        if (isIndexingRevisions()) {
            indexMessageService.addIndexForRepository(repositoryId);
            revisionIndexer.addRepository(repositoryId);
        }

        return subversionManager;
    }

    @VisibleForTesting
    protected SubversionManager createSubversionManager(final SvnProperties properties, final long repoId) {
        final PropertySet set = jiraPropertySetFactory.buildCachingPropertySet(REPO_PROPERTY, repoId);
        return new SubversionManagerImpl(repoId, SvnProperties.Util.fillPropertySet(properties, set));
    }

    public SubversionManager updateRepository(long repoId, SvnProperties properties) {
        final SubversionManager subversionManager = getRepository(repoId);
        subversionManager.update(properties);

        cachedSvnManagers.reset();
        return subversionManager;
    }

    public void removeRepository(long repositoryId) {
        final PropertySet original = jiraPropertySetFactory.buildCachingPropertySet(REPO_PROPERTY, repositoryId);

        try {
            indexMessageService.removeIndexForRepository(repositoryId);
            revisionIndexer.removeEntries(cachedSvnManagers.get().get(repositoryId).getId());
            original.remove();
            cachedSvnManagers.reset();
        } catch (Exception e) {
            throw new InfrastructureException("Could not remove repository index", e);
        }
    }

    public boolean isIndexingRevisions() {
        return revisionIndexer != null;
    }

    public RevisionIndexer getRevisionIndexer() {
        return revisionIndexer;
    }

    public Collection<SubversionManager> getRepositoryList() {
        return cachedSvnManagers.get().values();
    }

    public SubversionManager getRepository(long id) {
        return cachedSvnManagers.get().get(id);
    }

    void startRevisionIndexer() {
        getRevisionIndexer().start();
    }
}
