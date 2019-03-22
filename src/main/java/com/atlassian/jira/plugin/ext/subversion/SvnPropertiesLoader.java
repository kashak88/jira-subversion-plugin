package com.atlassian.jira.plugin.ext.subversion;

import com.atlassian.core.util.ClassLoaderUtils;
import com.atlassian.jira.InfrastructureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * exists to load SubversionManagers the old way so that the MultipleSubversionRepositoryManagerImpl doesn't get krufted
 * up with a bunch of legacy code
 */
public class SvnPropertiesLoader {

    private final static Logger log = LoggerFactory.getLogger(SvnPropertiesLoader.class);

    public static final String PROPERTIES_FILE_NAME = "subversion-jira-plugin.properties";

    public static List<SubversionProperties> getSVNProperties() throws InfrastructureException {
        Properties allProps = System.getProperties();

        try {
            allProps.load(ClassLoaderUtils.getResourceAsStream(PROPERTIES_FILE_NAME, MultipleSubversionRepositoryManagerImpl.class));
        } catch (IOException e) {
            throw new InfrastructureException("Problem loading " + PROPERTIES_FILE_NAME + ".", e);
        }

        List propertyList = new ArrayList();
        SubversionProperties defaultProps = getSubversionProperty(-1, allProps);
        if (defaultProps != null) {
            propertyList.add(defaultProps);
        } else {
            log.error("Could not load properties from " + PROPERTIES_FILE_NAME);
            throw new InfrastructureException("Could not load properties from " + PROPERTIES_FILE_NAME);
        }
        SubversionProperties prop;
        int i = 1;
        do {
            prop = getSubversionProperty(i, allProps);
            i++;
            if (prop != null) {
                prop.fillPropertiesFromOther(defaultProps);
                propertyList.add(prop);
            }
        }
        while (prop != null);

        return propertyList;
    }

    protected static SubversionProperties getSubversionProperty(int index, Properties props) {
        String indexStr = "." + Integer.toString(index);
        if (index == -1) {
            indexStr = "";
        }

        if (props.containsKey(MultipleSubversionRepositoryManager.SVN_ROOT_KEY + indexStr)) {
            final String svnRootStr = props.getProperty(MultipleSubversionRepositoryManager.SVN_ROOT_KEY + indexStr);
            final String displayName = props.getProperty(MultipleSubversionRepositoryManager.SVN_REPOSITORY_NAME + indexStr);

            final String changesetFormat = props.getProperty(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_CHANGESET + indexStr);
            final String fileAddedFormat = props.getProperty(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_ADDED + indexStr);
            final String fileModifiedFormat = props.getProperty(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_MODIFIED + indexStr);
            final String fileReplacedFormat = props.getProperty(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_REPLACED + indexStr);
            final String fileDeletedFormat = props.getProperty(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_DELETED + indexStr);

            final String username = props.getProperty(MultipleSubversionRepositoryManager.SVN_USERNAME_KEY + indexStr);
            final String password = props.getProperty(MultipleSubversionRepositoryManager.SVN_PASSWORD_KEY + indexStr);
            final String privateKeyFile = props.getProperty(MultipleSubversionRepositoryManager.SVN_PRIVATE_KEY_FILE + indexStr);
            Boolean revisionIndexing = null;
            if (props.containsKey(MultipleSubversionRepositoryManager.SVN_REVISION_INDEXING_KEY + indexStr)) {
                revisionIndexing = Boolean.valueOf("true".equalsIgnoreCase(props.getProperty(MultipleSubversionRepositoryManager.SVN_REVISION_INDEXING_KEY + indexStr)));
            }
            Integer revisionCacheSize = null;
            if (props.containsKey(MultipleSubversionRepositoryManager.SVN_REVISION_CACHE_SIZE_KEY + indexStr)) {
                revisionCacheSize = new Integer(props.getProperty(MultipleSubversionRepositoryManager.SVN_REVISION_CACHE_SIZE_KEY + indexStr));
            }

            return new SubversionProperties()
                    .setRoot(svnRootStr)
                    .setDisplayName(displayName)
                    .setChangeSetFormat(changesetFormat)
                    .setFileAddedFormat(fileAddedFormat)
                    .setFileModifiedFormat(fileModifiedFormat)
                    .setFileReplacedFormat(fileReplacedFormat)
                    .setFileDeletedFormat(fileDeletedFormat)
                    .setUsername(username)
                    .setPassword(password)
                    .setPrivateKeyFile(privateKeyFile)
                    .setRevisionIndexing(revisionIndexing)
                    .setRevisioningCacheSize(revisionCacheSize);

        } else {
            log.info("No " + MultipleSubversionRepositoryManager.SVN_ROOT_KEY + indexStr + " specified in " + PROPERTIES_FILE_NAME);
            return null;
        }
    }
}
