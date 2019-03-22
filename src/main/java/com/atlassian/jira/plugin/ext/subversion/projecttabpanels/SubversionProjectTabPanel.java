package com.atlassian.jira.plugin.ext.subversion.projecttabpanels;

import com.atlassian.jira.issue.index.IndexException;
import com.atlassian.jira.plugin.ext.subversion.MultipleSubversionRepositoryManager;
import com.atlassian.jira.plugin.ext.subversion.revisions.RevisionIndexer;
import com.atlassian.jira.plugin.projectpanel.ProjectTabPanel;
import com.atlassian.jira.plugin.projectpanel.impl.GenericProjectTabPanel;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.browse.BrowseContext;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.project.version.VersionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.BuildUtilsInfo;
import com.atlassian.jira.web.bean.I18nBean;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNLogEntry;
import webwork.action.ActionContext;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class provides a tab panel for the JIRA project view.
 *
 * @author Rolf Staflin
 * @version $Id$
 */
public class SubversionProjectTabPanel extends GenericProjectTabPanel implements ProjectTabPanel {
    private static final Logger log = LoggerFactory.getLogger(SubversionProjectTabPanel.class);

    private final MultipleSubversionRepositoryManager multipleSubversionRepositoryManager;

    private final VersionManager versionManager;

    private final PermissionManager permissionManager;

    private final BuildUtilsInfo buildUtilsInfo;

    /**
     * A special value for the &quot;selectedVersion&quot; request parameter that tells this panel
     * that it should return all commits for <em>all</em> issues in all versions.
     */
    public static final int ALL_VERSIONS = -1;

    /**
     * The initial number of commits to show initially.
     */
    public static final int NUMBER_OF_REVISIONS = 100;

    /**
     * The flag that indicates if archived versions should be considered when rendering commits. Currently
     * set to <tt>false</tt>.
     */
    public static final boolean INCLUDE_ARCHIVED_VERSIONS = false;

    /**
     * Instantiates a new instance of {@link com.atlassian.jira.plugin.ext.subversion.projecttabpanels.SubversionProjectTabPanel}.
     * All parameters are injected.
     *
     * @param authenticationContext               The authentication context.
     * @param multipleSubversionRepositoryManager The face to all the configured {@link com.atlassian.jira.plugin.ext.subversion.SubversionManager}.
     * @param versionManager                      The JIRA project version manager.
     * @param permissionManager                   The JIRA permission manager.
     */
    public SubversionProjectTabPanel(
            final JiraAuthenticationContext authenticationContext,
            final MultipleSubversionRepositoryManager multipleSubversionRepositoryManager,
            final VersionManager versionManager,
            final PermissionManager permissionManager,
            final BuildUtilsInfo buildUtilsInfo) {
        super(authenticationContext);
        this.multipleSubversionRepositoryManager = multipleSubversionRepositoryManager;
        this.versionManager = versionManager;
        this.permissionManager = permissionManager;
        this.buildUtilsInfo = buildUtilsInfo;
    }

    public String getHtml(BrowseContext browseContext) {
        if (log.isDebugEnabled())
            log.debug("Rendering commits for " + browseContext.getProject().getKey());

        Map<String, Object> startingParams = new HashMap<String, Object>();
        Project project = browseContext.getProject();
        String key = project.getKey();
        ApplicationUser user = browseContext.getUser();

        startingParams.put("action", getI18nBean(user));
        startingParams.put("project", project);
        startingParams.put("projectKey", key);
        startingParams.put("urlPrefix", getUrlPrefix());


        // Get selected versionNumber, if any
        startingParams.put("versionManager", versionManager);
        long versionNumber = getVersionRequestParameter();
        Version version = null;
        if (versionNumber != ALL_VERSIONS) {
            // The reason for the cast is Velocity's intelligence. It can't do Long comparisons.
            startingParams.put("versionNumber", (int) versionNumber);
            version = versionManager.getVersion(versionNumber);
            startingParams.put("selectedVersion", version);
        }

        // Get the list of recently updated issues and add it to the velocity context
        int pageSize = getPageSizeRequestParameter();
        List<SubversionProjectRevisionAction> recentCommits = getRecentCommits(key, version, user, getPageRequestParameter() * pageSize, pageSize + 1);

        if (recentCommits.size() > pageSize) {
            startingParams.put("moreAvailable", true);
            recentCommits = recentCommits.subList(0, pageSize);
        }

        startingParams.put("commits", recentCommits);

        // Get all versions. Used for the "Select versionNumber" drop-down list
        startingParams.put("releasedVersions", versionManager.getVersionsReleased(project.getId(), INCLUDE_ARCHIVED_VERSIONS));
        startingParams.put("unreleasedVersions", versionManager.getVersionsUnreleased(project.getId(), INCLUDE_ARCHIVED_VERSIONS));
        startingParams.put("stringUtils", new StringUtils());

        // Merge with velocity template and return HTML.
        return descriptor.getHtml("view", startingParams);
    }

    I18nBean getI18nBean(ApplicationUser user) {
        return new I18nBean(user);
    }

    /**
     * Looks up the latest commits for the curently selected project in each of the repositories.
     *
     * @param key        The JIRA project key of the currently selected project.
     * @param version    The JIRA project version to get commits for. If this is <code>null</code>,
     *                   the latest commits for the project as a whole are returned instead.
     * @param user       The remote user &mdash; we need to check that the user has "View Version Control" permission for an issue
     *                   before we show a commit for it.
     * @param startIndex For paging &mdash; The index of the entry that is the first result in the page desired.
     * @param pageSize   For paging &mdash; The size of the page.
     * @return A {@link java.util.List} of {@link SubversionProjectRevisionAction} objects,
     * each of which holds a valid {@link SVNLogEntry}.
     */
    private List<SubversionProjectRevisionAction> getRecentCommits(String key, Version version, ApplicationUser user, int startIndex, int pageSize) {
        if (log.isDebugEnabled())
            log.debug("Getting recent commits for project " + key + " and version " + version);

        List<SubversionProjectRevisionAction> actions = new ArrayList<SubversionProjectRevisionAction>();

        try {
            Map<Long, List<SVNLogEntry>> logEntries;
            RevisionIndexer indexer = multipleSubversionRepositoryManager.getRevisionIndexer();

            if (version == null) {
                logEntries = indexer.getLogEntriesByProject(key, user, startIndex, pageSize);
            } else {
                logEntries = indexer.getLogEntriesByVersion(version, user, startIndex, pageSize);
            }

            if (logEntries != null && logEntries.size() > 0) {
                for (Map.Entry<Long, List<SVNLogEntry>> entry : logEntries.entrySet()) {
                    long repoId = entry.getKey();

                    for (SVNLogEntry logEntry : entry.getValue())
                        actions.add(createProjectRevisionAction(repoId, logEntry));
                }
            }
        } catch (IndexException ie) {
            log.error("There' a problem with the index.", ie);
        } catch (IOException ioe) {
            log.error("Error reading the index.", ioe);
        }
        return actions;
    }

    SubversionProjectRevisionAction createProjectRevisionAction(long repoId, SVNLogEntry logEntry) {
        return new SubversionProjectRevisionAction(logEntry, multipleSubversionRepositoryManager, descriptor, repoId);
    }

    /**
     * Extracts the <code>selectedVersion</code> parameter from the HTTP request.
     * The versions are selected by a drop-down list on the SVN commit tab.
     *
     * @return A Long containing the parameter value, or <code>null</code> if
     * the parameter was not set or an error occurred while parsing the parameter.
     */
    private long getVersionRequestParameter() {
        long versionNumber = 0;
        HttpServletRequest request = ActionContext.getRequest();

        if (request != null) {
            String selectedVersion = request.getParameter("selectedVersion");
            if (StringUtils.isNotBlank(selectedVersion)) {
                try {
                    versionNumber = Long.parseLong(selectedVersion);
                } catch (NumberFormatException e) {
                    log.error("Unknown version string: " + selectedVersion, e);
                }
            }
        }

        return versionNumber;
    }

    private int getPageRequestParameter() {
        HttpServletRequest req = ActionContext.getRequest();

        if (null != req) {
            String pageIndexString = req.getParameter("pageIndex");
            return StringUtils.isBlank(pageIndexString) ? 0 : Integer.parseInt(pageIndexString);
        }

        return 0;
    }

    private int getPageSizeRequestParameter() {
        HttpServletRequest req = ActionContext.getRequest();

        if (null != req) {
            String pageIndexString = req.getParameter("pageSize");
            return StringUtils.isBlank(pageIndexString) ? NUMBER_OF_REVISIONS : Integer.parseInt(pageIndexString);
        }

        return NUMBER_OF_REVISIONS;
    }

    @Override
    public boolean showPanel(BrowseContext browseContext) {
        return multipleSubversionRepositoryManager.isIndexingRevisions() &&
                permissionManager.hasPermission(Permissions.VIEW_VERSION_CONTROL, browseContext.getProject(), browseContext.getUser());
    }

    private  String getUrlPrefix() {
        if (buildUtilsInfo.getVersion().startsWith("6.3")) {
            return "browse";
        } else {
            return "projects";
        }
    }
}
