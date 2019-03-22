/*
 * Created by IntelliJ IDEA.
 * User: Mike
 * Date: Sep 30, 2004
 * Time: 8:13:56 AM
 */
package com.atlassian.jira.plugin.ext.subversion;

import com.atlassian.jira.InfrastructureException;
import com.atlassian.jira.plugin.ext.subversion.linkrenderer.LinkFormatRenderer;
import com.atlassian.jira.plugin.ext.subversion.linkrenderer.NullLinkRenderer;
import com.atlassian.jira.plugin.ext.subversion.linkrenderer.SubversionLinkRenderer;
import com.opensymphony.module.propertyset.PropertySet;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.map.LRUMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SubversionManagerImpl implements SubversionManager {
    private final static Logger log = LoggerFactory.getLogger(SubversionManagerImpl.class);

    private SubversionLinkRenderer linkRenderer;
    private Map logEntryCache;
    private SVNRepository repository;

    private boolean active;
    private String inactiveMessage;
    private long id;

    private PropertySet properties;

    private ViewLinkFormat viewLinkFormat = null;
    private boolean isViewLinkSet = false;

    public SubversionManagerImpl(long id, PropertySet props) {
        this.id = id;
        this.properties = props;
        setup();
    }

    public synchronized void update(SvnProperties props) {
        deactivate(null);

        SvnProperties.Util.fillPropertySet(props, properties);
        isViewLinkSet = false; /* If we don't reset this flag, we get SVN-190 */

        setup();
    }

    protected void setup() {
        // Now setup web link renderer
        linkRenderer = null;

        if (getViewLinkFormat() != null)
            linkRenderer = new LinkFormatRenderer(this);
        else
            linkRenderer = new NullLinkRenderer();

        // Now setup revision indexing if they want it
        // Setup the log message cache
        int cacheSize = 10000;
        if (getRevisioningCacheSize() > 0) {
            cacheSize = getRevisioningCacheSize();
        }

        logEntryCache = new LRUMap(cacheSize);
        activate();
    }

    public synchronized void getLogEntries(long revision, final SvnEntryHandler svnEntryHandler) {

        // if connection isn't up, don't even try
        if (!isActive()) {
            return;
        }

        long latestRevision;
        try {
            latestRevision = repository.getLatestRevision();
        } catch (SVNException e) {
            // connection was active, but apparently now it's not
            log.error("Error getting the latest revision from the repository.", e);
            deactivate(e.getMessage());
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("Latest revision in repository=" + getRoot() + "  is : " + latestRevision);
        }

        if (latestRevision > 0 && latestRevision <= revision) {
            if (log.isDebugEnabled()) {
                log.debug("Have all the commits for repository=" + getRoot() + " - doing nothing.");
            }
            return;
        }

        long retrieveStart = revision + 1;
        if (retrieveStart < 0) {
            retrieveStart = 0;
        }

        if (log.isDebugEnabled()) {
            log.debug("Retrieving revisions to index (between " + retrieveStart + " and " + latestRevision + ") for repository=" + getRoot());
        }

        final AtomicInteger count = new AtomicInteger();
        try {
            repository.log(new String[]{""}, retrieveStart, latestRevision, true, true, new ISVNLogEntryHandler() {
                public void handleLogEntry(SVNLogEntry logEntry) {
                    if (log.isDebugEnabled()) {
                        log.debug("Retrieved #" + logEntry.getRevision() + " : " + logEntry.getMessage());
                    }

                    svnEntryHandler.handle(logEntry);
                    count.incrementAndGet();
                }
            });
        } catch (SVNException e) {
            log.error("Error retrieving changes from the repository.", e);
            deactivate(e.getMessage());
        }
        if (log.isDebugEnabled()) {
            log.debug("Retrieved " + count.get() + " revisions to index (between " + retrieveStart + " and " + latestRevision + ") from repository=" + getRoot());
        }

        // temp log comment
        if (log.isDebugEnabled()) {
            log.debug("log entries size = " + count.get() + " for " + getRoot());
        }
    }

    public synchronized SVNLogEntry getLogEntry(long revision) {
        if (!isActive()) {
            throw new IllegalStateException("The connection to the repository is not active");
        }
        final SVNLogEntry[] logEntry = new SVNLogEntry[]{(SVNLogEntry) logEntryCache.get(new Long(revision))};

        if (logEntry[0] == null) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("No cache - retrieving log message for revision: " + revision);
                }

                repository.log(new String[]{""}, revision, revision, true, true, new ISVNLogEntryHandler() {
                    public void handleLogEntry(SVNLogEntry entry) {
                        logEntry[0] = entry;
                        ensureCached(entry);
                    }
                });
            } catch (SVNException e) {
                log.error("Error retrieving logs: " + e, e);
                deactivate(e.getMessage());
                throw new InfrastructureException(e);
            }
        } else if (log.isDebugEnabled()) {
            log.debug("Found cached log message for revision: " + revision);
        }
        return logEntry[0];
    }


    public long getId() {
        return id;
    }

    /**
     * Make sure a single log message is cached.
     */
    private void ensureCached(SVNLogEntry logEntry) {
        synchronized (logEntryCache) {
            logEntryCache.put(new Long(logEntry.getRevision()), logEntry);
        }
    }

    public PropertySet getProperties() {
        return properties;
    }

    public String getDisplayName() {
        return !properties.exists(MultipleSubversionRepositoryManager.SVN_REPOSITORY_NAME) ? getRoot() : properties.getString(MultipleSubversionRepositoryManager.SVN_REPOSITORY_NAME);
    }

    public String getRoot() {
        return properties.getString(MultipleSubversionRepositoryManager.SVN_ROOT_KEY);
    }

    public String getUsername() {
        return properties.getString(MultipleSubversionRepositoryManager.SVN_USERNAME_KEY);
    }

    public String getPassword() {
        try {
            return decryptPassword(properties.getString(MultipleSubversionRepositoryManager.SVN_PASSWORD_KEY));
        } catch (IOException e) {
            log.error("Couldn't decrypt the password. Reseting it to null.", e);
            return null;
        }
    }

    public int getRevisioningCacheSize() {
        return properties.getInt(MultipleSubversionRepositoryManager.SVN_REVISION_CACHE_SIZE_KEY);
    }

    public String getPrivateKeyFile() {
        return properties.getString(MultipleSubversionRepositoryManager.SVN_PRIVATE_KEY_FILE);
    }

    public boolean isActive() {
        return active;
    }

    public String getInactiveMessage() {
        return inactiveMessage;
    }

    public void activate() {
        try {
            final SVNURL url = parseSvnUrl();
            repository = createRepository(url);
            final ISVNAuthenticationManager authManager;
            if (null != getPrivateKeyFile()) {
                authManager = new BasicAuthenticationManager(getUsername(), new File(getPrivateKeyFile()), getPassword(), 22);
            } else {
                authManager = SVNWCUtil.createDefaultAuthenticationManager(getUsername(), getPassword());
            }
            repository.setAuthenticationManager(new ISVNAuthenticationManagerDelegator(authManager));

            repository.testConnection();
            active = true;
        } catch (SVNException e) {
            log.error("Connection to Subversion repository " + getRoot() + " failed: " + e, e);
            // We don't want to throw an exception here because then the system won't start if the repo is down
            // or there is something wrong with the configuration.  We also still want this repository to show up
            // in our configuration so the user has a chance to fix the problem.
            active = false;
            inactiveMessage = e.getMessage();
        }
    }

    SVNURL parseSvnUrl()
            throws SVNException {
        return SVNURL.parseURIEncoded(getRoot());
    }

    SVNRepository createRepository(SVNURL url)
            throws SVNException {
        return SVNRepositoryFactory.create(url);
    }

    private void deactivate(String message) {
        if (repository != null) {
//			try {
            repository.closeSession();
//			}
//			catch (SVNException e) {
            // ignore, we're throwing the repository away anyways
//			}
            repository = null;
        }
        active = false;
        inactiveMessage = message;
    }

    public ViewLinkFormat getViewLinkFormat() {
        if (!isViewLinkSet) {
            final String type = properties.getString(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_TYPE);
            final String linkPathFormat = properties.getString(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_PATH_KEY);
            final String changesetFormat = properties.getString(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_CHANGESET);
            final String fileAddedFormat = properties.getString(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_ADDED);
            final String fileModifiedFormat = properties.getString(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_MODIFIED);
            final String fileReplacedFormat = properties.getString(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_REPLACED);
            final String fileDeletedFormat = properties.getString(MultipleSubversionRepositoryManager.SVN_LINKFORMAT_FILE_DELETED);

            if (linkPathFormat != null || changesetFormat != null || fileAddedFormat != null || fileModifiedFormat != null || fileReplacedFormat != null || fileDeletedFormat != null)
                viewLinkFormat = new ViewLinkFormat(type, changesetFormat, fileAddedFormat, fileModifiedFormat, fileReplacedFormat, fileDeletedFormat, linkPathFormat);
            else
                viewLinkFormat = null; /* [SVN-190] This could happen if the user clears all the fields in the Subversion repository web link configuration */
            isViewLinkSet = true;
        }

        return viewLinkFormat;
    }

    public SubversionLinkRenderer getLinkRenderer() {
        return linkRenderer;
    }


    protected static String decryptPassword(String encrypted) throws IOException {
        if (encrypted == null)
            return null;

        byte[] result = Base64.decodeBase64(encrypted);

        return new String(result, 0, result.length);
    }

    protected static String encryptPassword(String password) {
        if (password == null)
            return null;

        return Base64.encodeBase64String(password.getBytes());
    }

}
