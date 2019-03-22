package com.atlassian.jira.plugin.ext.subversion;

import com.opensymphony.module.propertyset.map.MapPropertySet;
import junit.framework.TestCase;

import java.util.HashMap;

public class TestSvnProperties extends TestCase {
    public void testNullDisplayName() {


        MapPropertySet propertySet = new MapPropertySet();
        propertySet.setMap(new HashMap());

        SubversionProperties properties = new SubversionProperties()
                .setRoot("/root/repo")
                .setUsername("user")
                .setPassword("password")
                .setRevisionIndexing(Boolean.TRUE)
                .setRevisioningCacheSize(new Integer(1000));

        SvnProperties.Util.fillPropertySet(properties, propertySet);

        assertEquals("/root/repo", propertySet.getString(MultipleSubversionRepositoryManager.SVN_ROOT_KEY));
        assertEquals("/root/repo", propertySet.getString(MultipleSubversionRepositoryManager.SVN_REPOSITORY_NAME));
    }
}
