package it.com.atlassian.jira.plugin.ext.subversion;

import com.atlassian.jira.functest.framework.FuncTestCase;
import com.atlassian.jira.functest.framework.locator.XPathLocator;
import com.atlassian.jira.rest.api.issue.IssueCreateResponse;
import com.atlassian.jira.rest.api.issue.IssueFields;
import com.atlassian.jira.rest.api.issue.ResourceRef;
import com.atlassian.jira.testkit.client.Backdoor;
import com.atlassian.jira.testkit.client.util.TestKitLocalEnvironmentData;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

public class TestSubversionPlugin extends FuncTestCase {
    private static final String SUBVERSION_REVISION_INDEXING_SERVICE_NAME = "Subversion Revision Indexing Service";

    private String svnRepositoryUrl;

    private SVNRepository repository = null;

    private Properties testSubversionConfiguration;

    private Backdoor testKit;

    private void setupSubversionTestConfiguration() {
        InputStream in = null;

        try {
            testSubversionConfiguration = new Properties();

            in = getClass().getClassLoader().getResourceAsStream("subversion-jira-plugin.properties");
            assertNotNull(in);

            testSubversionConfiguration.load(in);

        } catch (final IOException ioe) {
            fail("Unable to get test SVN URL: " + ioe.getMessage());
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    @Override
    protected void setUpTest() {
        super.setUpTest();

        testKit = new Backdoor(new TestKitLocalEnvironmentData());

        setupSubversionTestConfiguration();

        try {
            svnRepositoryUrl = "file://" + testSubversionConfiguration.getProperty("svn.test.root.path");
            assertNotNull(svnRepositoryUrl);

            /* Just in case stuff is left over */
            removeSvnRepository();

            DAVRepositoryFactory.setup();
            SVNRepositoryFactoryImpl.setup();
            FSRepositoryFactory.setup();
            repository = SVNRepositoryFactory.create(SVNRepositoryFactory.createLocalRepository(
                    new File(new URI(svnRepositoryUrl)),
                    true, false));
            ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager("dchui", "changeit");
            repository.setAuthenticationManager(authManager);


        } catch (SVNException e) {
            throw new RuntimeException("SVN error", e);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to remove SVN repository previously created by integeration tests at: " + svnRepositoryUrl, ioe);
        } catch (URISyntaxException use) {
            throw new RuntimeException("Property svn.root not setup to point to a local repository for tests.", use);
        }

        testKit.dataImport().restoreBlankInstance();
    }

    @Override
    protected void tearDownTest() {
        try {
            // https://studio.plugins.atlassian.com/browse/SVN-258
            // Library upgrade broke some test. By removing the SVN Directory would be sufficient for the next test to execute.
            removeSvnRepository();
        } catch (IOException e) {
            throw new RuntimeException("Generic IO error", e);
        } catch (URISyntaxException use) {
            throw new RuntimeException("Invalid svn.root specified in jira-subversion-plugin.properties.", use);
        } finally {
            super.tearDownTest();
        }
    }

    private int registerSubversionRepository(final int lastRepoId) {
        navigation.gotoPage("/secure/ViewSubversionRepositories.jspa");
        navigation.clickLinkWithExactText("Add");
        tester.setWorkingForm("jiraform");
        tester.setFormElement("displayName", testSubversionConfiguration.getProperty("svn.display.name"));
        tester.setFormElement("root", testSubversionConfiguration.getProperty("svn.root"));
        tester.setFormElement("username", testSubversionConfiguration.getProperty("svn.username"));
        tester.setFormElement("password", testSubversionConfiguration.getProperty("svn.password"));

//            tester.setFormElement("viewFormat", "http:///fisheye/viewrep/public${path}");
        tester.setFormElement("changesetFormat", testSubversionConfiguration.getProperty("linkformat.changeset"));
        tester.setFormElement("fileAddedFormat", testSubversionConfiguration.getProperty("linkformat.file.added"));
        tester.setFormElement("fileModifiedFormat", testSubversionConfiguration.getProperty("linkformat.file.modified"));
        tester.setFormElement("fileReplacedFormat", testSubversionConfiguration.getProperty("linkformat.file.replaced"));
        tester.setFormElement("fileDeletedFormat", testSubversionConfiguration.getProperty("linkformat.file.deleted"));
        tester.submit("add");
        return (lastRepoId + 1);
    }

    protected void removeSvnRepository() throws IOException, URISyntaxException {
        final File unzipDestination = new File(new URI(svnRepositoryUrl));

        if (unzipDestination.exists()) {
            FileUtils.deleteDirectory(unzipDestination);
        }
    }

    private void unregisterSubversionRepository(final int repoId) {
        navigation.gotoPage("/secure/DeleteSubversionRepository!default.jspa?repoId=" + repoId);
        tester.setWorkingForm("jiraform");
        tester.submit("delete");
    }

    /**
     * Anti test for <a href="http://jira.atlassian.com/browse/SVN-93">SVN-93</a>
     *
     * @throws SVNException         Thrown if the is a problem performing SVN operations.
     * @throws InterruptedException Thrown if the wait for reindexing throws it.
     */
    public void testCommitWithJiraIssueKeysInLowerCase() throws SVNException, InterruptedException {
        //we need to prepare data before we add repository as there is no easy way to trigger indexing afterwards
        createLogEntry("hsp-1, ahsp-1 A commit message with a valid JIRA issue key but in lower case form.");

        final int repoId = registerSubversionRepository(0);
        waitForReindex();

        testKit.project().addProject("Another Homosapiens", "AHSP", "admin");

        testKit.issues().createIssue("AHSP", "Some issue in AHSP");
        testKit.issues().createIssue("HSP", "Some issue in HSP");

        navigation.issue().gotoIssue("HSP-1");
        tester.assertLinkPresentWithText("Subversion");
        tester.clickLinkWithText("Subversion");

        text.assertTextPresent("A commit message with a valid JIRA issue key but in lower case form.");
        tester.assertLinkPresentWithText("AHSP-1");

        /* Check at AHSP-1, and the subversion issue tab panel should already be selected. */
        navigation.issue().gotoIssue("AHSP-1");
        text.assertTextPresent("A commit message with a valid JIRA issue key but in lower case form.");
        tester.assertLinkPresentWithText("HSP-1");

        /* Check if the issues are rendered properly in the Subversion project tab panel */
        navigation.gotoPage("/browse/HSP?selectedTab=com.atlassian.jira.plugin.ext.subversion:subversion-project-tab");
        text.assertTextPresent("A commit message with a valid JIRA issue key but in lower case form.");

        navigation.gotoPage("/browse/AHSP?selectedTab=com.atlassian.jira.plugin.ext.subversion:subversion-project-tab");
        text.assertTextPresent("A commit message with a valid JIRA issue key but in lower case form.");

        unregisterSubversionRepository(repoId);
    }

    public void testIssueTabPanelForCommitMessages() throws SVNException, InterruptedException {

        final int repoId = registerSubversionRepository(0);
        waitForReindex();
        testKit.issues().createIssue("HSP", "Some issue in HSP");
        navigation.issue().gotoIssue("HSP-1");

        // Browse and make sure nothing exists on the tab

        // Assert the Subversion tab exists
        tester.assertLinkPresentWithText("Subversion");

        tester.clickLinkWithText("Subversion");

        // Assert that no log entries exist
        tester.assertTextPresent("There are no subversion log entries for this issue yet.");

        // Connect to SVN and create a log entrywait
        createLogEntry("HSP-1 adding a svn log message");

        createModifyLogEntry("HSP-1 a modify svn log message");

        // NOTE: this is trying to create a log entry that will be marked as replace but the api is a bit unclear
        // as to how to do this, it does not work at the moment.
        //createReplaceLogEntry("HSP-1 a modify svn log message");

        createRemoveLogEntry("HSP-1 a remove svn log message");

        // To trigger reindex we need to remove and add repository again
        unregisterSubversionRepository(repoId);
        final int nextRepoId = registerSubversionRepository(repoId);
        waitForReindex();
        ;

        // Borwse and verify the commit log exists
        navigation.issue().gotoIssue("HSP-1");

        // Assert the Subversion tab exists
        text.assertTextPresent("Subversion");

        // Assert that an add log entry exists
        text.assertTextPresent("adding a svn log message");
        text.assertTextPresent("ADD");
        // Make sure that the two add viewcvs links are present and in the correct format
        text.assertTextPresent("http://svn.atlassian.com/fisheye/changelog/public?cs=1"); /* Changeset  */
        text.assertTextPresent("http://svn.atlassian.com/fisheye/viewrep/public/test/file.txt?r=1"); /* First file */
        text.assertTextPresent("http://svn.atlassian.com/fisheye/viewrep/public/test?r=1"); /* Second file */

        // Assert that a modify log entry exists
        text.assertTextPresent("modify svn log message");
        text.assertTextPresent("MODIFY");
        // Make sure that the modify viewcvs link exists
        text.assertTextPresent("http://svn.atlassian.com/fisheye/viewrep/public/test/file.txt#2");

        // Assert that a delete log entry exists
        text.assertTextPresent("remove svn log message");
        text.assertTextPresent("DEL");
        // Make sure that the del viewcvs link exists
        text.assertTextPresent("http://svn.atlassian.com/fisheye/viewrep/public/test");

        unregisterSubversionRepository(nextRepoId);
    }

    public void testIssueTabPanelWithSimilarProjectKeysInCommitMessages() throws SVNException, InterruptedException {
        testKit.project().addProject("Another Homo", "AHSP", "admin");
        testKit.issues().createIssue("AHSP", "A new issue");
        testKit.issues().createIssue("HSP", "A new issue");

        createLogEntry("This is a commit for AHSP-1.");
        createModifyLogEntry("HSP-1 a modify svn log message");
        createModifyLogEntry("This is an entry with a URL http://jira.atlassian.com/browse/HSP-1");

        final int repoId = registerSubversionRepository(0);
        waitForReindex();

        navigation.gotoPage("/browse/HSP-1?page=com.atlassian.jira.plugin.ext.subversion:subversion-commits-tabpanel");

        text.assertTextPresent("a modify svn log message");
        text.assertTextNotPresent("AHSP-1");
        text.assertTextNotPresent("This is an entry with a URL");

        navigation.gotoPage("/browse/AHSP-1?page=com.atlassian.jira.plugin.ext.subversion:subversion-commits-tabpanel");
        text.assertTextNotPresent("a modify svn log message");
        text.assertTextNotPresent("This is an entry with a URL");
        text.assertTextPresent("This is a commit for");

        createRemoveLogEntry("Removed.");
        unregisterSubversionRepository(repoId);
    }

    public void testProjectTabPanelWithSimilarProjectKeysInCommitMessages() throws SVNException, InterruptedException {
        testKit.project().addProject("Another Homo", "AHSP", "admin");
        testKit.issues().createIssue("AHSP", "A new issue");
        testKit.issues().createIssue("HSP", "A new issue");

        createLogEntry("This is a commit for AHSP-1.");
        createModifyLogEntry("HSP-1 a modify svn log message");
        createModifyLogEntry("This is an entry with a URL http://jira.atlassian.com/browse/HSP-1");

        final int repoId = registerSubversionRepository(0);
        waitForReindex();

        navigation.gotoPage(gotoSubversionTabForProject("HSP"));
        text.assertTextPresent("a modify svn log message");
        text.assertTextNotPresent("AHSP");
        text.assertTextNotPresent("This is an entry with a URL");

        navigation.gotoPage(gotoSubversionTabForProject("AHSP"));
        text.assertTextNotPresent("a modify svn log message");
        text.assertTextNotPresent("This is an entry with a URL");
        text.assertTextPresent("This is a commit for");

        createRemoveLogEntry("Removed.");
        unregisterSubversionRepository(repoId);
    }

    public void testFilterCommitMessagesInProjectTabPanelByVersion() throws SVNException, InterruptedException {
        final String versionId2;
        final String versionId1;

        testKit.project().addProject("Another Homosapiens", "AHSP", "admin");
        administration.project().addVersion("AHSP", "1.0", null, null);
        administration.project().addVersion("AHSP", "2.0", null, null);

        final IssueCreateResponse ir1 = testKit.issues().createIssue("AHSP", "Summary Of Bug 1");
        final IssueFields issueFields1 = new IssueFields();
        issueFields1.versions(ResourceRef.withName("1.0"));
        testKit.issues().setIssueFields(ir1.key, issueFields1);

        final IssueCreateResponse ir2 = testKit.issues().createIssue("AHSP", "Summary Of Bug 2");
        final IssueFields issueFields2 = new IssueFields();
        issueFields2.versions(ResourceRef.withName("2.0"));
        testKit.issues().setIssueFields(ir2.key, issueFields2);

        createLogEntry("This is a commit for AHSP-1.");
        createModifyLogEntry("This is a commit for AHSP-2.");

        final int repoId = registerSubversionRepository(0);
        waitForReindex();

        navigation.gotoPage(gotoSubversionTabForProject("AHSP"));
        versionId1 = new XPathLocator(tester, "//select[@name='selectedVersion']//option[text()='1.0']/@value").getText();
        versionId2 = new XPathLocator(tester, "//select[@name='selectedVersion']//option[text()='2.0']/@value").getText();

        tester.assertLinkPresentWithText("AHSP-1");
        tester.assertLinkPresentWithText("AHSP-2");

        navigation.gotoPage(gotoSubversionTabForProject("AHSP") + "&selectedVersion=" + versionId1);
        tester.assertLinkPresentWithText("AHSP-1");
        tester.assertLinkNotPresentWithText("AHSP-2");

        navigation.gotoPage(gotoSubversionTabForProject("AHSP") + "&selectedVersion=" + versionId2);
        tester.assertLinkNotPresentWithText("AHSP-1");
        tester.assertLinkPresentWithText("AHSP-2");
        unregisterSubversionRepository(repoId);
    }

    private void createRemoveLogEntry(String message) throws SVNException {
        ISVNEditor editor = repository.getCommitEditor(message, null);

        deleteDir(editor, "test");
    }

    private void createModifyLogEntry(String message) throws SVNException {
        ISVNEditor editor = repository.getCommitEditor(message, null);

        byte[] modifiedContents = "This is the same file but modified a little.".getBytes();

        modifyFile(editor, "test", "test/file.txt", modifiedContents);
    }


    private void waitForReindex() throws InterruptedException {
        Thread.sleep(5000);
    }

    private void createLogEntry(String message) throws SVNException {

        ISVNEditor editor = repository.getCommitEditor(message, null);

        /*
        * Add a directory and a file within that directory.
        *
        * SVNCommitInfo object contains basic information on the committed revision, i.e.
        * revision number, author name, commit date and commit message.
        */

        /*
        * Sample file contents.
        */
        byte[] contents = "This is a new file".getBytes();


        SVNCommitInfo commitInfo = addDir(editor, "test", "test/file.txt", contents);
    }

    /*
    * This method performs commiting an addition of a  directory  containing  a
    * file.
    */
    private SVNCommitInfo addDir(ISVNEditor editor, String dirPath,
                                 String filePath, byte[] data) throws SVNException {
        /*
         * Always called first. Opens the current root directory. It  means  all
         * modifications will be applied to this directory until  a  next  entry
         * (located inside the root) is opened/added.
         *
         * -1 - revision is HEAD, of course (actually, for a comit  editor  this
         * number is irrelevant)
         */
        editor.openRoot(-1);
        /*
         * Adds a new directory to the currently opened directory (in this  case
         * - to the  root  directory  for which the SVNRepository was  created).
         * Since this moment all changes will be applied to this new  directory.
         *
         * dirPath is relative to the root directory.
         *
         * copyFromPath (the 2nd parameter) is set to null and  copyFromRevision
         * (the 3rd) parameter is set to  -1  since  the  directory is not added
         * with history (is not copied, in other words).
         */
        if (dirPath != null) {
            editor.addDir(dirPath, null, -1);
        }

        /*
         * Adds a new file to the just added  directory. The  file  path is also
         * defined as relative to the root directory.
         *
         * copyFromPath (the 2nd parameter) is set to null and  copyFromRevision
         * (the 3rd parameter) is set to -1 since  the file is  not  added  with
         * history.
         */
        editor.addFile(filePath, null, -1);
        /*
         * The next steps are directed to applying delta to the  file  (that  is
         * the full contents of the file in this case).
         */
        editor.applyTextDelta(filePath, null);
        /*
         * Use delta generator utility class to generate and send delta
         *
         * Note that you may use only 'target' data to generate delta when there is no
         * access to the 'base' (previous) version of the file. However, using 'base'
         * data will result in smaller network overhead.
         *
         * SVNDeltaGenerator will call editor.textDeltaChunk(...) method for each generated
         * "diff window" and then editor.textDeltaEnd(...) in the end of delta transmission.
         * Number of diff windows depends on the file size.
         *
         */
        SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
        String checksum = deltaGenerator.sendDelta(filePath, new ByteArrayInputStream(data), editor, true);

        /*
        * Closes the new added file.
        */
        editor.closeFile(filePath, checksum);
        if (dirPath != null) {
            /*
            * Closes the new added directory.
            */
            editor.closeDir();
        }

        /*
        * Closes the root directory.
        */
        editor.closeDir();
        /*
        * This is the final point in all editor handling. Only now all that new
        * information previously described with the editor's methods is sent to
        * the server for committing. As a result the server sends the new
        * commit information.
        */
        return editor.closeEdit();
    }

    /*
    * This method performs committing file modifications.
    */
    private SVNCommitInfo modifyFile(ISVNEditor editor, String dirPath,
                                     String filePath, byte[] newData) throws SVNException {
        /*
         * Always called first. Opens the current root directory. It  means  all
         * modifications will be applied to this directory until  a  next  entry
         * (located inside the root) is opened/added.
         *
         * -1 - revision is HEAD
         */
        editor.openRoot(-1);
        /*
         * Opens a next subdirectory (in this example program it's the directory
         * added  in  the  last  commit).  Since this moment all changes will be
         * applied to this directory.
         *
         * dirPath is relative to the root directory.
         * -1 - revision is HEAD
         */
        editor.openDir(dirPath, -1);
        /*
         * Opens the file added in the previous commit.
         *
         * filePath is also defined as a relative path to the root directory.
         */
        editor.openFile(filePath, -1);

        /*
        * The next steps are directed to applying and writing the file delta.
        */
        editor.applyTextDelta(filePath, null);

        /*
        * Use delta generator utility class to generate and send delta
        *
        * Note that you may use only 'target' data to generate delta when there is no
        * access to the 'base' (previous) version of the file. However, using 'base'
        * data will result in smaller network overhead.
        *
        * SVNDeltaGenerator will call editor.textDeltaChunk(...) method for each generated
        * "diff window" and then editor.textDeltaEnd(...) in the end of delta transmission.
        * Number of diff windows depends on the file size.
        *
        */
        SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
        String checksum = deltaGenerator.sendDelta(filePath, new ByteArrayInputStream(newData), editor, true);

        /*
        * Closes the file.
        */
        editor.closeFile(filePath, checksum);

        /*
        * Closes the directory.
        */
        editor.closeDir();

        /*
        * Closes the root directory.
        */
        editor.closeDir();

        /*
        * This is the final point in all editor handling. Only now all that new
        * information previously described with the editor's methods is sent to
        * the server for committing. As a result the server sends the new
        * commit information.
        */
        return editor.closeEdit();
    }

    /*
    * This method performs committing a deletion of a directory.
    */
    private SVNCommitInfo deleteDir(ISVNEditor editor, String dirPath) throws SVNException {
        /*
         * Always called first. Opens the current root directory. It  means  all
         * modifications will be applied to this directory until  a  next  entry
         * (located inside the root) is opened/added.
         *
         * -1 - revision is HEAD
         */
        editor.openRoot(-1);
        /*
         * Deletes the subdirectory with all its contents.
         *
         * dirPath is relative to the root directory.
         */
        editor.deleteEntry(dirPath, -1);

        /*
        * Closes the root directory.
        */
        editor.closeDir();
        /*
        * This is the final point in all editor handling. Only now all that new
        * information previously described with the editor's methods is sent to
        * the server for committing. As a result the server sends the new
        * commit information.
        */

        return editor.closeEdit();
    }

    private String gotoSubversionTabForProject(String projectKey) {

        int version = parseVersion(backdoor.applicationProperties().getString("jira.version").substring(0, 3));
        System.out.println(" +++ ");
        if (version >= 64) {
            return "/projects/" + projectKey + "?selectedItem=com.atlassian.jira.plugin.ext.subversion:subversion-project-tab";
        } else {
            return "/browse/" + projectKey + "?selectedTab=com.atlassian.jira.plugin.ext.subversion:subversion-project-tab";
        }


    }


    private int parseVersion(String s) {
        final String[] split = s.split("\\.");
        final Integer mainVersion = Integer.valueOf(split[0]) * 10;
        final Integer dotVersion = Integer.valueOf(split[1]);

        return mainVersion + dotVersion;
    }

//    /*
//    * This method performs committing file modifications.
//    */
//    private SVNCommitInfo replaceFile(ISVNEditor editor, String filePath, byte[] newData) throws SVNException {
//        deleteDir(editor, filePath);
//        addDir(editor, null, filePath, newData, false);
//        editor.closeDir();
//        return editor.closeEdit();
//    }

//    /*
//    * This  method  performs how a directory in the repository can be copied to
//    * branch.
//    */
//    private SVNCommitInfo copyDir(ISVNEditor editor, String srcDirPath,
//                                         String dstDirPath, long revision) throws SVNException {
//        /*
//         * Always called first. Opens the current root directory. It  means  all
//         * modifications will be applied to this directory until  a  next  entry
//         * (located inside the root) is opened/added.
//         *
//         * -1 - revision is HEAD
//         */
//        editor.openRoot(-1);
//
//        /*
//        * Adds a new directory that is a copy of the existing one.
//        *
//        * srcDirPath   -  the  source  directory  path (relative  to  the  root
//        * directory).
//        *
//        * dstDirPath - the destination directory path where the source will be
//        * copied to (relative to the root directory).
//        *
//        * revision    - the number of the source directory revision.
//        */
//        editor.addDir(dstDirPath, srcDirPath, revision);
//        /*
//         * Closes the just added copy of the directory.
//         */
//        editor.closeDir();
//        /*
//         * Closes the root directory.
//         */
//        editor.closeDir();
//        /*
//         * This is the final point in all editor handling. Only now all that new
//         * information previously described with the editor's methods is sent to
//         * the server for committing. As a result the server sends the new
//         * commit information.
//         */
//        return editor.closeEdit();
//    }
//
//
//    private void createReplaceLogEntry(String message) throws SVNException
//    {
//        ISVNEditor editor = repository.getCommitEditor(message, null);
//        byte[] contents = "This is a new file".getBytes();
//        replaceFile(editor, "test/file.txt", contents);
//    }

}
