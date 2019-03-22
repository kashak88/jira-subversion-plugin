package com.atlassian.jira.plugin.ext.subversion.linkrenderer;

import com.atlassian.core.util.StringUtils;
import com.atlassian.jira.plugin.ext.subversion.SubversionConstants;
import com.atlassian.jira.plugin.ext.subversion.SubversionManager;
import com.atlassian.jira.plugin.ext.subversion.ViewLinkFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;

/**
 * A link renderer implementation which lets the user specify the format in the properties file, to accommodate
 * various formats (ViewCVS, Fisheye, etc) out there.
 *
 * @author Chenggong Lu
 * @author Jeff Turner
 */
public class LinkFormatRenderer implements SubversionLinkRenderer {
    private static Logger log = LoggerFactory.getLogger(LinkFormatRenderer.class);
    private String pathLinkFormat;
    private String fileReplacedFormat;
    private String fileAddedFormat;
    private String fileModifiedFormat;
    private String fileDeletedFormat;
    private String changesetFormat;

    public LinkFormatRenderer(SubversionManager subversionManager) {

        ViewLinkFormat linkFormat = subversionManager.getViewLinkFormat();
        if (linkFormat != null) {
            if (linkFormat.getChangesetFormat() != null
                    && linkFormat.getChangesetFormat().trim().length() != 0) {
                changesetFormat = linkFormat.getChangesetFormat();
            }

            if (linkFormat.getFileAddedFormat() != null
                    && linkFormat.getFileAddedFormat().trim().length() != 0) {
                fileAddedFormat = linkFormat.getFileAddedFormat();
            }

            if (linkFormat.getFileModifiedFormat() != null
                    && linkFormat.getFileModifiedFormat().trim().length() != 0) {
                fileModifiedFormat = linkFormat.getFileModifiedFormat();
            }

            if (linkFormat.getFileReplacedFormat() != null
                    && linkFormat.getFileReplacedFormat().trim().length() != 0) {
                fileReplacedFormat = linkFormat.getFileReplacedFormat();
            }

            if (linkFormat.getFileDeletedFormat() != null
                    && linkFormat.getFileDeletedFormat().trim().length() != 0) {
                fileDeletedFormat = linkFormat.getFileDeletedFormat();
            }

            if (linkFormat.getViewFormat() != null
                    && linkFormat.getViewFormat().trim().length() != 0) {
                pathLinkFormat = linkFormat.getViewFormat();
            }

        } else {
            log.warn("viewLinkFormat is null");
        }
    }

    public String getCopySrcLink(SVNLogEntry revision, SVNLogEntryPath logEntryPath) {
        long revisionNumber = revision.getRevision();
        return linkPath(pathLinkFormat, logEntryPath.getCopyPath(), revisionNumber);
//                getPathLink(logEntryPath.getCopyPath());
    }

    public String getRevisionLink(SVNLogEntry revision) {
        return getRevisionLink(revision.getRevision());
    }

    public String getChangePathLink(SVNLogEntry revision, SVNLogEntryPath logEntryPath) {
        char changeType = logEntryPath.getType();
        String path = logEntryPath.getPath();
        long revisionNumber = revision.getRevision();

        if (changeType == SubversionConstants.MODIFICATION) {
            return linkPath(fileModifiedFormat, path, revisionNumber);
        } else if (changeType == SubversionConstants.ADDED) {
            return linkPath(fileAddedFormat, path, revisionNumber);
        } else if (changeType == SubversionConstants.REPLACED) {
            return linkPath(fileReplacedFormat, path, revisionNumber);
        } else if (changeType == SubversionConstants.DELETED) {
            return linkPath(fileDeletedFormat, path, revisionNumber);
        } else {
            return linkPath(fileReplacedFormat, path, revisionNumber);
        }
    }

    protected String getRevisionLink(long revisionNumber) {
        if (changesetFormat != null) {
            try {
                String href = StringUtils.replaceAll(changesetFormat, "${rev}", "" + revisionNumber);
                return "<a href=\"" + href + "\">#" + revisionNumber + "</a>";
            } catch (Exception ex) {
                log.error("format error: " + ex.getMessage(), ex);
            }
        }
        return "#" + revisionNumber;
    }

    private String linkPath(final String format, String path, long revisionNumber) {

        if (path != null && path.length() > 0 && path.charAt(0) != '/') {
            path = "/" + path;
        }

        if (format != null) {

            try {
                String href = format;
                if (path != null) {
                    href = StringUtils.replaceAll(href, "${path}", path);
                }
                href = StringUtils.replaceAll(href, "${rev}", "" + revisionNumber);
                href = StringUtils.replaceAll(href, "${rev-1}", "" + (revisionNumber - 1));

                return "<a href=\"" + href + "\">" + path + "</a>";
            } catch (Exception ex) {
                log.error("format error: " + ex.getMessage(), ex);
            }
        }
        return path;
    }
}
