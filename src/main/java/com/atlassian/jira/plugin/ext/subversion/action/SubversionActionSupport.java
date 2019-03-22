package com.atlassian.jira.plugin.ext.subversion.action;

import com.atlassian.jira.plugin.ext.subversion.MultipleSubversionRepositoryManager;
import com.atlassian.jira.plugin.ext.subversion.WebLinkType;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.velocity.htmlsafe.HtmlSafe;
import org.apache.commons.lang.StringEscapeUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Base class for the Subversion plugins actions.
 */
public class SubversionActionSupport extends JiraWebActionSupport {

    private MultipleSubversionRepositoryManager multipleRepoManager;
    private List webLinkTypes;

    public SubversionActionSupport(MultipleSubversionRepositoryManager manager) {
        this.multipleRepoManager = manager;
    }

    protected MultipleSubversionRepositoryManager getMultipleRepoManager() {
        return multipleRepoManager;
    }

    public boolean hasPermissions() {
        return hasPermission(Permissions.ADMINISTER);
    }

    public String doDefault() {
        if (!hasPermissions()) {
            return PERMISSION_VIOLATION_RESULT;
        }

        return INPUT;
    }

    public List getWebLinkTypes() throws IOException {
        if (webLinkTypes == null) {
            webLinkTypes = new ArrayList();
            Properties properties = new Properties();
            properties.load(getClass().getResourceAsStream("/weblinktypes.properties"));

            String[] types = properties.getProperty("types", "").split(" ");
            for (int i = 0; i < types.length; i++) {
                webLinkTypes.add(new WebLinkType(
                        types[i],
                        properties.getProperty(types[i] + ".name", types[i]),
                        properties.getProperty(types[i] + ".view"),
                        properties.getProperty(types[i] + ".changeset"),
                        properties.getProperty(types[i] + ".file.added"),
                        properties.getProperty(types[i] + ".file.modified"),
                        properties.getProperty(types[i] + ".file.replaced"),
                        properties.getProperty(types[i] + ".file.deleted")
                ));
            }
        }
        return webLinkTypes;
    }

    @HtmlSafe
    public String escapeJavaScript(String javascriptUnsafeString) {
        return StringEscapeUtils.escapeJavaScript(javascriptUnsafeString);
    }
}
