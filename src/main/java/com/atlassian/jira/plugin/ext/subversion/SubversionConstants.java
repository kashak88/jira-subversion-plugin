package com.atlassian.jira.plugin.ext.subversion;

/**
 * Letters indicating changes in the Subversion repository. Defined in
 * <a href="http://svnbook.red-bean.com/en/1.1/ch03s05.html#svn-ch-3-sect-5.1">the Subversion book</a>.
 */
public interface SubversionConstants {
    char MODIFICATION = 'M';
    char ADDED = 'A';
    char DELETED = 'D';
    char REPLACED = 'R';
}
