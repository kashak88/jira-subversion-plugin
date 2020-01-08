Notes:
The reason this plugin doesn't work in Jira 8.0.X is because org.apache.lucene was upgraded from 3.3.0 to 7.3.0. Filter class that is used by this plugin no longer exists in 7.3.0. See https://confluence.atlassian.com/adminjira/lucene-upgrade-955171970.html . Instead of reworking this plugin, we're including org.apache.lucene 3.3.0 dependency to be bundled with the .jar
While this solution seems to work, use at own risk. I am a sys admin, not a developer.
Due to many requests, I am including the built .jar

Build steps:
1) Build was done on Ubuntu 16.04 using openjdk-8-jdk and Atlassian SDK 8.0.7. Install atlassian SDK: https://developer.atlassian.com/server/framework/atlassian-sdk/downloads/
2) Clone this repository. cd into the cloned directory.
3) run atlas-compile
4) run atlas-package
5) This will create a .jar file inside ./target directory. This is your compiled plugin.

Installation steps:
1) Upgrade Jira to 8.0.X (This was tested on 8.0.2). Obviously try this on a clone/QA server first
2) Uninstall Jira Subversion Plugin (Your subversion settings will be saved)
3) remove caches: rm -rf /var/atlassian/application-data/jira/caches/indexesV1/plugins/atlassian-subversion-revisions/
4) Manually upload the .jar file that you've created. This should re-create the indexes
5) Plugin modules should load without exceptions and you should be able to see commits under Subversion tab in your tickets.

JIRA Subversion Plugin
=======================
JIRA's Subversion integration lets you see Subversion commit information relevant to each JIRA issue.

This plugin is no longer being actively developed, and there are no plans to make it compatible with JIRA 7.4 or above.
If you'd like to work on the plugin, feel free to fork this repository.

Documentation
-------------
https://ecosystem.atlassian.net/wiki/display/SVN/JIRA+Subversion+plugin

Marketplace
-----------
Binary version of this plugin are available on Marketplace:
https://marketplace.atlassian.com/plugins/com.atlassian.jira.plugin.ext.subversion/server/overview

Support
-----------
Plugin is unsupported by Atlassian
