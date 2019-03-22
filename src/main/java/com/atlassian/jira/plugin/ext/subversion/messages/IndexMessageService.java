package com.atlassian.jira.plugin.ext.subversion.messages;

/**
 * Service responsible for sending messages to cluster nodes
 * about changes in subversion repository list. It is client
 * responsibility to register and unregister listeners properly.
 *
 * @since v2.0
 */
public interface IndexMessageService {

    /**
     * Sends cluster message to inform nodes that subversion index
     * needs to be removed for a repository with given id
     *
     * @param repositoryId
     */
    void removeIndexForRepository(long repositoryId);

    /**
     * Sends cluster message to inform nodes that subversion index
     * needs to be created for a repository with given id
     *
     * @param repositoryId
     */
    void addIndexForRepository(long repositoryId);

    /**
     * Registers listener that handles cluster messages related to indexing
     */
    void registerListeners();

    /**
     * Unregisters listener that handles cluster messages related to indexing
     */
    void unregisterListeners();
}
