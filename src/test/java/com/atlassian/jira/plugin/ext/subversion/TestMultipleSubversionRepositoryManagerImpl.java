package com.atlassian.jira.plugin.ext.subversion;

import com.atlassian.beehive.ClusterLock;
import com.atlassian.beehive.ClusterLockService;
import com.atlassian.cache.CacheManager;
import com.atlassian.cache.CacheSettings;
import com.atlassian.cache.CachedReference;
import com.atlassian.cache.Supplier;
import com.atlassian.jira.cluster.ClusterMessagingService;
import com.atlassian.jira.config.util.IndexPathManager;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.changehistory.ChangeHistoryManager;
import com.atlassian.jira.issue.index.IndexException;
import com.atlassian.jira.plugin.ext.subversion.messages.IndexMessageService;
import com.atlassian.jira.plugin.ext.subversion.revisions.RevisionIndexer;
import com.atlassian.jira.project.version.VersionManager;
import com.atlassian.jira.propertyset.JiraPropertySetFactory;
import com.atlassian.jira.security.PermissionManager;
import com.google.common.collect.Maps;
import com.opensymphony.module.propertyset.PropertySet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @since v2.0
 */
@RunWith(MockitoJUnitRunner.class)
public class TestMultipleSubversionRepositoryManagerImpl {

    @Mock
    private VersionManager versionManager;
    @Mock
    private IssueManager issueManager;
    @Mock
    private PermissionManager permissionManager;
    @Mock
    private ChangeHistoryManager changeHistoryManager;
    @Mock
    private JiraPropertySetFactory jiraPropertySetFactory;
    @Mock
    private IndexPathManager indexPathManager;
    @Mock
    private CacheManager cacheManager;

    @Mock
    private CachedReference<Map<Long, SubversionManager>> cachedReference;

    @Mock
    private IndexMessageService indexMessageService;

    @Mock
    private RevisionIndexer revisionIndexer;

    @Mock
    private ClusterLockService clusterLockService;
    @Mock
    private ClusterMessagingService clusterMessageService;

    @Mock
    private Supplier<Map<Long, SubversionManager>> supplier;

    @Mock
    private PropertySet propertySet;

    @Mock
    private SubversionManager subversionManager;

    @Mock
    private ClusterLock clusteredLock;

    @Mock
    private SvnProperties svnProperties;

    private Map<Long, SubversionManager> managersMap;

    private MultipleSubversionRepositoryManagerImpl multipleSubversionRepositoryManager;

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        when(cacheManager.getCachedReference(eq(MultipleSubversionRepositoryManagerImpl.class), eq("cachedSvnManagers"), any(Supplier.class), any(CacheSettings.class))).thenReturn(cachedReference);

        multipleSubversionRepositoryManager = new MultipleSubversionRepositoryManagerImpl(versionManager, issueManager, permissionManager, changeHistoryManager, jiraPropertySetFactory, indexPathManager, cacheManager, clusterLockService, clusterMessageService) {
            @Override
            protected SubversionManager createSubversionManager(final SvnProperties properties, final long repoId) {
                return subversionManager;
            }
        };

        final Field indexMessageServiceFiled = multipleSubversionRepositoryManager.getClass().getSuperclass().getDeclaredField("indexMessageService");
        indexMessageServiceFiled.setAccessible(true);
        indexMessageServiceFiled.set(multipleSubversionRepositoryManager, indexMessageService);

        final Field revisionIndexerField = multipleSubversionRepositoryManager.getClass().getSuperclass().getDeclaredField("revisionIndexer");
        revisionIndexerField.setAccessible(true);
        revisionIndexerField.set(multipleSubversionRepositoryManager, revisionIndexer);

        managersMap = Maps.newHashMap();
        managersMap.put(1L, subversionManager);
    }

    @Test
    public void getRepositoryListFromCachedReference() {
        when(cachedReference.get()).thenReturn(managersMap);

        final Collection<SubversionManager> result = multipleSubversionRepositoryManager.getRepositoryList();

        assertThat(managersMap.values(), is(result));
        verify(cachedReference).get();
    }

    @Test
    public void createAndIndexRepository() {
        when(clusterLockService.getLockForName(MultipleSubversionRepositoryManagerImpl.REPOSITORY_INDEX_LOCK)).thenReturn(clusteredLock);
        when(jiraPropertySetFactory.buildNoncachingPropertySet(MultipleSubversionRepositoryManagerImpl.APP_PROPERTY_PREFIX)).thenReturn(propertySet);
        when(propertySet.getLong(MultipleSubversionRepositoryManagerImpl.LAST_REPO_ID)).thenReturn(452L);

        multipleSubversionRepositoryManager.createRepository(svnProperties);


        InOrder inOrder = inOrder(clusterLockService, revisionIndexer, propertySet, cachedReference, clusteredLock, indexMessageService);

        inOrder.verify(clusteredLock).lock();
        inOrder.verify(propertySet).getLong(MultipleSubversionRepositoryManagerImpl.LAST_REPO_ID);
        inOrder.verify(propertySet).setLong(MultipleSubversionRepositoryManagerImpl.LAST_REPO_ID, 453L);
        inOrder.verify(clusteredLock).unlock();
        inOrder.verify(cachedReference).reset();
        inOrder.verify(indexMessageService).addIndexForRepository(453L);
        inOrder.verify(revisionIndexer).addRepository(453L);
    }

    @Test
    public void updateRepository() {
        when(cachedReference.get()).thenReturn(managersMap);

        multipleSubversionRepositoryManager.updateRepository(1L, svnProperties);

        InOrder inOrder = inOrder(cachedReference, subversionManager);
        inOrder.verify(subversionManager).update(svnProperties);
        inOrder.verify(cachedReference).reset();
    }

    @Test
    public void removeRepository() throws SVNException, IndexException, IOException {
        when(jiraPropertySetFactory.buildCachingPropertySet(MultipleSubversionRepositoryManagerImpl.REPO_PROPERTY, 1L)).thenReturn(propertySet);
        when(cachedReference.get()).thenReturn(managersMap);
        when(subversionManager.getId()).thenReturn(10L);

        multipleSubversionRepositoryManager.removeRepository(1L);

        InOrder inOrder = inOrder(indexMessageService, revisionIndexer, propertySet, cachedReference);

        inOrder.verify(indexMessageService).removeIndexForRepository(1L);
        inOrder.verify(revisionIndexer).removeEntries(10L);
        inOrder.verify(propertySet).remove();
        inOrder.verify(cachedReference).reset();
    }

    @Test
    public void checkIfListenersAreRemoved() throws Exception {
        multipleSubversionRepositoryManager.destroy();
        verify(indexMessageService, atMost(1)).unregisterListeners();
    }

}
