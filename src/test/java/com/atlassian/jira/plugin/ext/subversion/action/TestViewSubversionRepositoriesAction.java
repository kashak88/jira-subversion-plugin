package com.atlassian.jira.plugin.ext.subversion.action;

import com.atlassian.jira.plugin.ext.subversion.MultipleSubversionRepositoryManager;
import com.atlassian.jira.plugin.ext.subversion.SubversionManager;
import org.jmock.Mock;
import org.jmock.MockObjectTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestViewSubversionRepositoriesAction extends MockObjectTestCase {
    private ViewSubversionRepositoriesAction viewSubversionRepositoriesAction;

    private Mock mockMultipleSubversionManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mockMultipleSubversionManager = new Mock(MultipleSubversionRepositoryManager.class);


        viewSubversionRepositoriesAction = new ViewSubversionRepositoriesAction((MultipleSubversionRepositoryManager) mockMultipleSubversionManager.proxy()) {
            protected <T> T getComponentInstanceOfType(Class<T> clazz) {
                return null;
            }
        };
    }

    public void testRepositoriesSortedByName() {
        Mock mockRepoC = new Mock(SubversionManager.class);
        Mock mockRepoB = new Mock(SubversionManager.class);
        Mock mockRepoA = new Mock(SubversionManager.class);

        mockRepoC.expects(atLeastOnce()).method("getDisplayName").will(returnValue("C"));
        mockRepoB.expects(atLeastOnce()).method("getDisplayName").will(returnValue("B"));
        mockRepoA.expects(atLeastOnce()).method("getDisplayName").will(returnValue("A"));

        mockMultipleSubversionManager.expects(once()).method("getRepositoryList").withNoArguments().will(
                returnValue(
                        Arrays.asList(
                                mockRepoC.proxy(),
                                mockRepoA.proxy(),
                                mockRepoB.proxy()
                        )
                )
        );

        List<SubversionManager> svnMgrs = new ArrayList<SubversionManager>(viewSubversionRepositoriesAction.getRepositories());

        assertEquals(3, svnMgrs.size());

        assertEquals("A", svnMgrs.get(0).getDisplayName());
        assertEquals("B", svnMgrs.get(1).getDisplayName());
        assertEquals("C", svnMgrs.get(2).getDisplayName());
    }
}
