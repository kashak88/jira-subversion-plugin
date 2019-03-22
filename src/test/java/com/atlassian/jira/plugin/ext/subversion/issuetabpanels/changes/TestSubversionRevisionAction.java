package com.atlassian.jira.plugin.ext.subversion.issuetabpanels.changes;

import com.atlassian.jira.plugin.ext.subversion.MultipleSubversionRepositoryManager;
import org.jmock.Mock;
import org.jmock.MockObjectTestCase;
import org.tmatesoft.svn.core.SVNLogEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class TestSubversionRevisionAction extends MockObjectTestCase {
    private SubversionRevisionAction subversionRevisionAction;

    private SVNLogEntry logEntry;

    private Mock mockMultipleSubversionRepositoryManager;

    private List<String> issueKeys;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mockMultipleSubversionRepositoryManager = new Mock(MultipleSubversionRepositoryManager.class);

        logEntry = new SVNLogEntry(new HashMap(), 1, "", new Date(), "");
        issueKeys = new ArrayList<String>();

        subversionRevisionAction = new SubversionRevisionAction(
                logEntry,
                (MultipleSubversionRepositoryManager) mockMultipleSubversionRepositoryManager.proxy(),
                null,
                1) {
            @Override
            List<String> getIssueKeysFromCommitMessage(String logMessageUpperCase) {
                return issueKeys;
            }
        };
    }

    public void testIssueKeysAreUpperCased() {
        String commitMessage = "CHG: Version 0.14.49-SNAPSHOT\n" +
                "FIX: In den englischen I18N-Texten des KampagnenManagers war der Key 'description.campaign.period' fehlerhaft. Es fehlte ein Backslash vorm Ende der Zeile. fffdemo-157\n" +
                "FIX: In den englischen I18N-Texten des CM war der Key 'date.hint' fehlerhaft.\n" +
                "FIX: CSS f\u00fcr den CM gefixt. Au\u00dferdem hab ich das CSS mal auf mehrere Dateien verteilt.\n" +
                "ADD: Neue Einstellung im CM, um Kampagnen freizugeben, d.h. zu sperren, bis sie bewu\u00dft freigegeben wurden.\n" +
                "ADD: Neue Funktion im CM, um die Anzahl Treffer von gepushten Produkten zu beschr\u00e4nken. fffcm-19\n" +
                "CHG: fffcm-15 (Datenbankkonverter soll pro DB-File nur noch die Kampagnen des aktuellen Mandanten \u00fcbersetzen): War eh schon deprecated, jetzt ist die Funktion, die das gemacht hat, raus";

        issueKeys.addAll(Arrays.asList("FFFDEMO-157", "FFFCM-19", "FFFCM-15"));

        String rewrittenCommitMessage = subversionRevisionAction.rewriteLogMessage(commitMessage);
        assertEquals(
                "CHG: Version 0.14.49-SNAPSHOT\n" +
                        "FIX: In den englischen I18N-Texten des KampagnenManagers war der Key 'description.campaign.period' fehlerhaft. Es fehlte ein Backslash vorm Ende der Zeile. FFFDEMO-157\n" +
                        "FIX: In den englischen I18N-Texten des CM war der Key 'date.hint' fehlerhaft.\n" +
                        "FIX: CSS f\u00fcr den CM gefixt. Au\u00dferdem hab ich das CSS mal auf mehrere Dateien verteilt.\n" +
                        "ADD: Neue Einstellung im CM, um Kampagnen freizugeben, d.h. zu sperren, bis sie bewu\u00dft freigegeben wurden.\n" +
                        "ADD: Neue Funktion im CM, um die Anzahl Treffer von gepushten Produkten zu beschr\u00e4nken. FFFCM-19\n" +
                        "CHG: FFFCM-15 (Datenbankkonverter soll pro DB-File nur noch die Kampagnen des aktuellen Mandanten \u00fcbersetzen): War eh schon deprecated, jetzt ist die Funktion, die das gemacht hat, raus",
                rewrittenCommitMessage);
    }

    public void testParenthesisInCommitMessageDoesNotRaisePatternSyntaxException() {
        String commitMessage = "CHG: Version 0.14.49-SNAPSHOT\n" +
                "FIX: In den englischen I18N-Texten des KampagnenManagers war der Key 'description.campaign.period' fehlerhaft. Es fehlte ein Backslash vorm Ende der Zeile. FFFDEMO-157\n" +
                "FIX: In den englischen I18N-Texten des CM war der Key 'date.hint' fehlerhaft.\n" +
                "FIX: CSS f\u00fcr den CM gefixt. Au\u00dferdem hab ich das CSS mal auf mehrere Dateien verteilt.\n" +
                "ADD: Neue Einstellung im CM, um Kampagnen freizugeben, d.h. zu sperren, bis sie bewu\u00dft freigegeben wurden.\n" +
                "ADD: Neue Funktion im CM, um die Anzahl Treffer von gepushten Produkten zu beschr\u00e4nken. FFFCM-19\n" +
                "CHG: FFFCM-15 (Datenbankkonverter soll pro DB-File nur noch die Kampagnen des aktuellen Mandanten \u00fcbersetzen): War eh schon deprecated, jetzt ist die Funktion, die das gemacht hat, raus";

        issueKeys.addAll(Arrays.asList("FFFDEMO-157", "FFFCM-19", "FFFCM-15"));

        String rewrittenCommitMessage = subversionRevisionAction.rewriteLogMessage(commitMessage);
        assertEquals(commitMessage, rewrittenCommitMessage);
    }

    public void testAccentedCharactersDoNotCauseStringIndexOutOfBounds() {
        String commitMessage = "tst-1 \u00df tst-1 \u00e4 TST-1 \u00f6 TsT-1 \u00fc";

        issueKeys.addAll(Arrays.asList("TST-1"));

        String rewrittenCommitMessage = subversionRevisionAction.rewriteLogMessage(commitMessage);
        assertEquals("TST-1 \u00df TST-1 \u00e4 TST-1 \u00f6 TST-1 \u00fc", rewrittenCommitMessage);
    }
}
