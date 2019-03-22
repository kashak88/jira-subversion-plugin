package com.atlassian.jira.plugin.ext.subversion;

import org.jmock.Mock;
import org.jmock.MockObjectTestCase;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;

import java.util.Properties;

public class TestISVNAuthenticationManagerDelegator extends MockObjectTestCase {
    private ISVNAuthenticationManagerDelegator isvnAuthenticationManagerDelegator;

    private Mock mockISVNAuthenticationManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mockISVNAuthenticationManager = new Mock(ISVNAuthenticationManager.class);
        isvnAuthenticationManagerDelegator = new ISVNAuthenticationManagerDelegator(
                (ISVNAuthenticationManager) mockISVNAuthenticationManager.proxy()
        );
    }

    public void testGetDefaultConnectionTimeout() {
        assertEquals(120000, isvnAuthenticationManagerDelegator.getConnectTimeout(null));
    }

    public void testGetDefaultReadTimeout() {
        assertEquals(120000, isvnAuthenticationManagerDelegator.getReadTimeout(null));
    }

    public void testGetReadTimeoutFromSystemProperty() {
        Properties systemProperties = System.getProperties();
        String systemProperty = "com.atlassian.jira.plugin.ext.subversion.svnreadtimeout";

        systemProperties.setProperty(systemProperty, String.valueOf(15000));
        assertEquals(15000, isvnAuthenticationManagerDelegator.getReadTimeout(null));

        systemProperties.remove(systemProperty);
    }

    public void testReadTimeoutDefaultsIfSystemPropertyOverrideIsNotAnInteger() {
        Properties systemProperties = System.getProperties();
        String systemProperty = "com.atlassian.jira.plugin.ext.subversion.svnreadtimeout";

        systemProperties.setProperty(systemProperty, "invalid");
        assertEquals(120000, isvnAuthenticationManagerDelegator.getReadTimeout(null));

        systemProperties.remove(systemProperty);
    }

    public void testReadTimeoutDefaultsIfSystemPropertyOverrideIsLesserThanZero() {
        Properties systemProperties = System.getProperties();
        String systemProperty = "com.atlassian.jira.plugin.ext.subversion.svnreadtimeout";

        systemProperties.setProperty(systemProperty, "-1");
        assertEquals(120000, isvnAuthenticationManagerDelegator.getReadTimeout(null));

        systemProperties.remove(systemProperty);
    }

    public void testGetConnectionTimeoutFromSystemProperty() {
        Properties systemProperties = System.getProperties();
        String systemProperty = "com.atlassian.jira.plugin.ext.subversion.svnconnectiontimeout";

        systemProperties.setProperty(systemProperty, String.valueOf(15000));
        assertEquals(15000, isvnAuthenticationManagerDelegator.getConnectTimeout(null));

        systemProperties.remove(systemProperty);
    }

    public void testConnectionTimeoutDefaultsIfSystemPropertyOverrideIsNotAnInteger() {
        Properties systemProperties = System.getProperties();
        String systemProperty = "com.atlassian.jira.plugin.ext.subversion.svnconnectiontimeout";

        systemProperties.setProperty(systemProperty, "invalid");
        assertEquals(120000, isvnAuthenticationManagerDelegator.getConnectTimeout(null));

        systemProperties.remove(systemProperty);
    }

    public void testConnectionTimeoutDefaultsIfSystemPropertyOverrideIsLesserThanZero() {
        Properties systemProperties = System.getProperties();
        String systemProperty = "com.atlassian.jira.plugin.ext.subversion.svnconnectiontimeout";

        systemProperties.setProperty(systemProperty, "-1");
        assertEquals(120000, isvnAuthenticationManagerDelegator.getConnectTimeout(null));

        systemProperties.remove(systemProperty);
    }

    public void testOtherMethodsDelegated() throws SVNException {
        mockISVNAuthenticationManager.expects(once()).method("setAuthenticationProvider").withAnyArguments();
        mockISVNAuthenticationManager.expects(once()).method("getProxyManager").withAnyArguments().will(returnValue(null));
        mockISVNAuthenticationManager.expects(once()).method("getTrustManager").withAnyArguments().will(returnValue(null));
        mockISVNAuthenticationManager.expects(once()).method("getFirstAuthentication").withAnyArguments().will(returnValue(null));
        mockISVNAuthenticationManager.expects(once()).method("getNextAuthentication").withAnyArguments().will(returnValue(null));
        mockISVNAuthenticationManager.expects(once()).method("acknowledgeAuthentication").withAnyArguments();
        mockISVNAuthenticationManager.expects(once()).method("acknowledgeTrustManager").withAnyArguments();
        mockISVNAuthenticationManager.expects(once()).method("isAuthenticationForced").withNoArguments().will(returnValue(false));

        isvnAuthenticationManagerDelegator.setAuthenticationProvider(null);
        assertNull(isvnAuthenticationManagerDelegator.getProxyManager(null));
        assertNull(isvnAuthenticationManagerDelegator.getTrustManager(null));
        assertNull(isvnAuthenticationManagerDelegator.getFirstAuthentication(null, null, null));
        assertNull(isvnAuthenticationManagerDelegator.getNextAuthentication(null, null, null));

        isvnAuthenticationManagerDelegator.acknowledgeAuthentication(false, null, null, null, null);
        isvnAuthenticationManagerDelegator.acknowledgeTrustManager(null);
        assertFalse(isvnAuthenticationManagerDelegator.isAuthenticationForced());
    }
}
