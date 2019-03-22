package com.atlassian.jira.plugin.ext.subversion;

import com.opensymphony.module.propertyset.PropertySet;


public interface SvnProperties {
    String getRoot();

    String getDisplayName();

    String getUsername();

    String getPassword();

    Boolean getRevisionIndexing();

    Integer getRevisionCacheSize();

    String getPrivateKeyFile();

    String getWebLinkType();

    String getChangesetFormat();

    String getFileAddedFormat();

    String getViewFormat();

    String getFileModifiedFormat();

    String getFileReplacedFormat();

    String getFileDeletedFormat();

    static class Util {
        static PropertySet fillPropertySet(SvnProperties properties, PropertySet propertySet) {
            propertySet.setString(MultipleSubversionRepositoryManager.SVN_ROOT_KEY, properties.getRoot());
            propertySet.setString(MultipleSubversionRepositoryManager.SVN_REPOSITORY_NAME, properties.getDisplayName() != null ? properties.getDisplayName() : properties.getRoot());
            propertySet.setString(MultipleSubversionRepositoryManager.SVN_USERNAME_KEY, properties.getUsername());
            propertySet.setString(MultipleSubversionRepositoryManager.SVN_PASSWORD_KEY, SubversionManagerImpl.encryptPassword(properties.getPassword()));
            propertySet.setBoolean(MultipleSubversionRepositoryManager.SVN_REVISION_INDEXING_KEY, properties.getRevisionIndexing().booleanValue());
            propertySet.setInt(MultipleSubversionRepositoryManager.SVN_REVISION_CACHE_SIZE_KEY, properties.getRevisionCacheSize().intValue());
            propertySet.setString(MultipleSubversionRepositoryManager.SVN_PRIVATE_KEY_FILE, properties.getPrivateKeyFile());
            propertySet.setString(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_TYPE, properties.getWebLinkType());
            propertySet.setString(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_PATH_KEY, properties.getViewFormat()); /* SVN-190 */
            propertySet.setString(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_CHANGESET, properties.getChangesetFormat());
            propertySet.setString(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_ADDED, properties.getFileAddedFormat());
            propertySet.setString(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_MODIFIED, properties.getFileModifiedFormat());
            propertySet.setString(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_REPLACED, properties.getFileReplacedFormat());
            propertySet.setString(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_DELETED, properties.getFileDeletedFormat());
            return propertySet;
        }
    }
}
