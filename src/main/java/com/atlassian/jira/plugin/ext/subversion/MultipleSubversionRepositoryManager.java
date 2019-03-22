package com.atlassian.jira.plugin.ext.subversion;

import com.atlassian.jira.plugin.ext.subversion.revisions.RevisionIndexer;

import java.util.Collection;

/**
 * Main component of the Subversion plugin.
 */
public interface MultipleSubversionRepositoryManager {
    String SVN_ROOT_KEY = "svn.root";
    String SVN_REPOSITORY_NAME = "svn.display.name";
    String SVN_USERNAME_KEY = "svn.username";
    String SVN_PASSWORD_KEY = "svn.password";
    String SVN_PRIVATE_KEY_FILE = "svn.privatekeyfile";
    String SVN_REVISION_INDEXING_KEY = "revision.indexing";
    String SVN_REVISION_CACHE_SIZE_KEY = "revision.cache.size";

    String SVN_LINKFORMAT_TYPE = "linkformat.type";
    String SVN_LINKFORMAT_CHANGESET = "linkformat.changeset";
    String SVN_LINKFORMAT_FILE_ADDED = "linkformat.file.added";
    String SVN_LINKFORMAT_FILE_MODIFIED = "linkformat.file.modified";
    String SVN_LINKFORMAT_FILE_REPLACED = "linkformat.file.replaced";
    String SVN_LINKFORMAT_FILE_DELETED = "linkformat.file.deleted";

    String SVN_LINKFORMAT_PATH_KEY = "linkformat.copyfrom";

    String SVN_LOG_MESSAGE_CACHE_SIZE_KEY = "logmessage.cache.size";

    boolean isIndexingRevisions();

    RevisionIndexer getRevisionIndexer();

    /**
     * Returns a Collection of SubversionManager instances, one for each repository.
     *
     * @return the managers.
     */
    Collection<SubversionManager> getRepositoryList();

    SubversionManager getRepository(long repoId);

    SubversionManager createRepository(SvnProperties props);

    SubversionManager updateRepository(long repoId, SvnProperties props);

    void removeRepository(long repoId);

}
