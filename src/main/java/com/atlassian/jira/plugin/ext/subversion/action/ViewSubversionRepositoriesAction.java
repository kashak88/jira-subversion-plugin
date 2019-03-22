package com.atlassian.jira.plugin.ext.subversion.action;

import com.atlassian.jira.plugin.ext.subversion.MultipleSubversionRepositoryManager;
import com.atlassian.jira.plugin.ext.subversion.SubversionManager;
import com.google.common.collect.Ordering;
import org.apache.commons.lang.StringUtils;

import java.util.Collection;
import java.util.Comparator;

/**
 * Manage 1 or more repositories
 */
public class ViewSubversionRepositoriesAction extends SubversionActionSupport {

    private static Comparator<SubversionManager> svnManagerComparator =
            new Comparator<SubversionManager>() {
                public int compare(SubversionManager left, SubversionManager right) {
                    return StringUtils.defaultString(left.getDisplayName()).compareTo(
                            StringUtils.defaultString(right.getDisplayName())
                    );
                }
            };

    public ViewSubversionRepositoriesAction(MultipleSubversionRepositoryManager manager) {
        super(manager);
    }

    public Collection<SubversionManager> getRepositories() {
        final Collection<SubversionManager> repositoryList = getMultipleRepoManager().getRepositoryList();
        return Ordering.from(svnManagerComparator).sortedCopy(repositoryList);

    }
}
