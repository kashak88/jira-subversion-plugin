package com.atlassian.jira.plugin.ext.subversion;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.ISVNProxyManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.io.SVNRepository;

import javax.net.ssl.TrustManager;
import java.util.Properties;

public class ISVNAuthenticationManagerDelegator implements ISVNAuthenticationManager {
    private static final Logger LOG = LoggerFactory.getLogger(ISVNAuthenticationManagerDelegator.class);

    private final ISVNAuthenticationManager delegate;

    private static final String SYSTEM_PROPERTY_SVN_READ_TIMEOUT = "com.atlassian.jira.plugin.ext.subversion.svnreadtimeout";

    private static final String SYSTEM_PROPERTY_SVN_CONNECTION_TIMEOUT = "com.atlassian.jira.plugin.ext.subversion.svnconnectiontimeout";

    private static final int DEFAULT_READ_TIMEOUT = 120000; // Read timeout defaults to 2 minutes

    private static final int DEFAULT_CONNECTION_TIMEOUT = DEFAULT_READ_TIMEOUT; // Connection timeout defaults to 2 minutes.

    ISVNAuthenticationManagerDelegator(ISVNAuthenticationManager delegate) {
        this.delegate = delegate;
    }

    public void setAuthenticationProvider(ISVNAuthenticationProvider isvnAuthenticationProvider) {
        delegate.setAuthenticationProvider(isvnAuthenticationProvider);
    }

    public ISVNProxyManager getProxyManager(SVNURL svnurl)
            throws SVNException {
        return delegate.getProxyManager(svnurl);
    }

    public TrustManager getTrustManager(SVNURL svnurl)
            throws SVNException {
        return delegate.getTrustManager(svnurl);
    }

    public SVNAuthentication getFirstAuthentication(String s, String s1, SVNURL svnurl)
            throws SVNException {
        return delegate.getFirstAuthentication(s, s1, svnurl);
    }

    public SVNAuthentication getNextAuthentication(String s, String s1, SVNURL svnurl)
            throws SVNException {
        return delegate.getNextAuthentication(s, s1, svnurl);
    }

    public void acknowledgeAuthentication(boolean b, String s, String s1, SVNErrorMessage svnErrorMessage, SVNAuthentication svnAuthentication)
            throws SVNException {
        delegate.acknowledgeAuthentication(b, s, s1, svnErrorMessage, svnAuthentication);
    }

    public void acknowledgeTrustManager(TrustManager trustManager) {
        delegate.acknowledgeTrustManager(trustManager);
    }

    public boolean isAuthenticationForced() {
        return delegate.isAuthenticationForced();
    }


    private int getTimeout(String systemProperty, int defaultValue) {
        int timeout = defaultValue;

        try {
            Properties systemProperties = System.getProperties();
            String timeoutString = StringUtils.trim(systemProperties.getProperty(systemProperty));
            if (StringUtils.isNotBlank(timeoutString)) {
                if ((timeout = Integer.parseInt(timeoutString)) < 0)
                    timeout = defaultValue;
            }
        } catch (NumberFormatException nfe) {
            LOG.warn("Unable to convert " + systemProperty + " to an int. The default of '" + defaultValue + "' will be used instead.");
        }

        return timeout;
    }

    public int getReadTimeout(SVNRepository svnRepository) {
        return getTimeout(SYSTEM_PROPERTY_SVN_READ_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    public int getConnectTimeout(SVNRepository svnRepository) {
        return getTimeout(SYSTEM_PROPERTY_SVN_CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT);
    }
}
