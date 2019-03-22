package com.atlassian.jira.plugin.ext.subversion.revisions.scheduling.clustersafe;

import com.atlassian.configurable.ObjectConfiguration;
import com.atlassian.configurable.ObjectConfigurationException;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.plugin.ext.subversion.MultipleSubversionRepositoryManager;
import com.atlassian.jira.plugin.ext.subversion.revisions.RevisionIndexer;
import com.atlassian.jira.service.AbstractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * Service responsible for indexing subversion repository
 *
 * @since v2.0
 **/
public class UpdateSvnIndexService extends AbstractService {

    private static final Logger logger = LoggerFactory.getLogger(UpdateSvnIndexService.class);
    public static final String SERVICE_NAME = "Subversion Index Update Service";

    private MultipleSubversionRepositoryManager multipleSubversionRepositoryManager;

    public UpdateSvnIndexService() {
        multipleSubversionRepositoryManager = ComponentAccessor.getOSGiComponentInstanceOfType(MultipleSubversionRepositoryManager.class);
    }

    @Override
    public void run() {

        if (logger.isInfoEnabled()) {
            logger.info("Indexing job started at {}", new Date());
        }

        try {
            final RevisionIndexer revisionIndexer = multipleSubversionRepositoryManager.getRevisionIndexer();
            if (revisionIndexer != null) {
                revisionIndexer.updateIndex();
            } else {
                logger.warn("Tried to index changes but SubversionManager has no revision indexer.");
            }
        } catch (Exception e) {
            logger.error("Error occurred while indexing changes: " + e);
        }
    }

    @Override
    public ObjectConfiguration getObjectConfiguration() throws ObjectConfigurationException {
        return getObjectConfiguration("svnupdateservice", "com/atlassian/jira/plugin/ext/subversion/action/UpdateSvnIndexService.xml", null);
    }

    public static String getServiceName() {
        return SERVICE_NAME;
    }
}
