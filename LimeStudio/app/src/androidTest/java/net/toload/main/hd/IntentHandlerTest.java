package net.toload.main.hd;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.view.View;
import android.widget.CheckBox;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import net.toload.main.hd.ui.LIMESettings;
import net.toload.main.hd.ui.IntentHandler;

import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

/**
 * Smoke tests for IntentHandler routing of external ACTION_VIEW imports.
 * 
 * <p>These tests verify that IntentHandler correctly routes different intent types
 * without crashing. They do not validate the complete import flow, only that the
 * routing logic handles various input types gracefully.
 * 
 * <p><b>Note</b>: Tests are ordered alphabetically. The first test sometimes times out
 * due to first-launch DEX bytecode verification overhead on slower emulators. This is
 * a known issue with Android instrumented testing and does not indicate a functional problem.
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@LargeTest
public class IntentHandlerTest {

    /**
     * Tests text/plain intent routing with .lime file.
     *
     * <p><b>Note</b>: This test has an extended timeout (60 seconds) to accommodate
     * DEX bytecode verification overhead on first process launch. The timeout is usually
     * only needed on slower emulators during the first test execution.
     */
    @Test(timeout = 60000)
    public void processTextPlainIntent_doesNotCrash() {
        try (ActivityScenario<LIMESettings> scenario = ActivityScenario.launch(LIMESettings.class)) {
            scenario.onActivity(activity -> {
                try {
                    File tmp = new File(activity.getCacheDir(), "test_import.lime");
                    try (FileOutputStream fos = new FileOutputStream(tmp)) {
                        fos.write("a\tb\n".getBytes(StandardCharsets.UTF_8));
                    }

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.fromFile(tmp), "text/plain");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    IntentHandler handler = new IntentHandler(activity, activity.getSetupImController());
                    handler.processIntent(intent);

                    // Close any dialog the handler may have shown to avoid lingering UI keeping the test alive
                    activity.getSupportFragmentManager().executePendingTransactions();
                    androidx.fragment.app.Fragment dialog = activity.getSupportFragmentManager().findFragmentByTag("ImportDialog");
                    if (dialog instanceof net.toload.main.hd.ui.dialog.ImportDialog) {
                        ((net.toload.main.hd.ui.dialog.ImportDialog) dialog).dismissAllowingStateLoss();
                    }

                    // If no exception is thrown, we consider the routing successful for this smoke test
                    assertTrue("IntentHandler should process text/plain without crashing", true);
                } catch (Exception e) {
                    throw new AssertionError("IntentHandler crashed processing text/plain intent", e);
                }
            });

            // Ensure all pending UI work completes before the scenario closes
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }

    @Test
    public void processTextPlainCinIntent_doesNotCrash() {
        try (ActivityScenario<LIMESettings> scenario = ActivityScenario.launch(LIMESettings.class)) {
            scenario.onActivity(activity -> {
                try {
                    File tmp = new File(activity.getCacheDir(), "test_import.cin");
                    try (FileOutputStream fos = new FileOutputStream(tmp)) {
                        fos.write("%keyname begin\na b\n%keyname end\n".getBytes(StandardCharsets.UTF_8));
                    }

                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, tmp.getAbsolutePath());

                    IntentHandler handler = new IntentHandler(activity, activity.getSetupImController());
                    handler.processIntent(intent);

                    assertTrue("IntentHandler should process text/plain .cin without crashing", true);
                } catch (Exception e) {
                    throw new AssertionError("IntentHandler crashed processing text/plain .cin intent", e);
                }
            });
        }
    }

    @Test
    public void processSupportedFileIntents_showImportDialogInsteadOfUsingFilename() {
        String[][] fixtures = new String[][]{
                {"renamed_db.limedb", "application/zip"},
                {"renamed_db.zip", "application/zip"},
                {"array.lime", "text/plain"},
                {"array.cin", "text/plain"}
        };

        for (String[] fixture : fixtures) {
            try (ActivityScenario<LIMESettings> scenario = ActivityScenario.launch(LIMESettings.class)) {
                scenario.onActivity(activity -> {
                    try {
                        File tmp = new File(activity.getCacheDir(), fixture[0]);
                        try (FileOutputStream fos = new FileOutputStream(tmp)) {
                            if (fixture[0].endsWith(".limedb") || fixture[0].endsWith(".zip")) {
                                fos.write(new byte[]{0x50, 0x4B, 0x03, 0x04}); // minimal zip signature
                            } else {
                                fos.write("q|一\n".getBytes(StandardCharsets.UTF_8));
                            }
                        }

                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.fromFile(tmp), fixture[1]);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        IntentHandler handler = new IntentHandler(activity, activity.getSetupImController());
                        handler.processIntent(intent);

                        activity.getSupportFragmentManager().executePendingTransactions();
                        androidx.fragment.app.Fragment dialog = activity.getSupportFragmentManager().findFragmentByTag("ImportDialog");
                        assertNotNull("IntentHandler should ask user to choose destination IM for " + fixture[0], dialog);
                        View dialogView = dialog.getView();
                        assertNotNull("ImportDialog view should be created", dialogView);
                        CheckBox restore = dialogView.findViewById(R.id.chkImportRestoreLearning);
                        assertNotNull("File import dialog should expose restore-learning option", restore);
                        assertTrue("Restore-learning option should be visible for file imports", restore.getVisibility() == View.VISIBLE);
                        assertTrue("Restore-learning should default on so non-empty table imports preserve learned data", restore.isChecked());
                        assertTrue("File import should allow selecting existing/non-empty destination tables", dialogView.findViewById(R.id.btnImportCustom).isEnabled());
                        if (dialog instanceof net.toload.main.hd.ui.dialog.ImportDialog) {
                            ((net.toload.main.hd.ui.dialog.ImportDialog) dialog).dismissAllowingStateLoss();
                        }
                    } catch (Exception e) {
                        throw new AssertionError("IntentHandler crashed processing supported file intent " + fixture[0], e);
                    }
                });
            }
        }
    }

    @Test
    public void processViewIntentWithInvalidScheme_gracefullyFails() {
        try (ActivityScenario<LIMESettings> scenario = ActivityScenario.launch(LIMESettings.class)) {
            scenario.onActivity(activity -> {
                try {
                    File tmp = new File(activity.getCacheDir(), "bad_scheme.lime");
                    try (FileOutputStream fos = new FileOutputStream(tmp)) {
                        fos.write("a\tb\n".getBytes(StandardCharsets.UTF_8));
                    }

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.fromFile(tmp), "text/plain");
                    intent.setData(Uri.parse("invalid://" + tmp.getName()));

                    IntentHandler handler = new IntentHandler(activity, activity.getSetupImController());
                    handler.processIntent(intent);

                    assertTrue("IntentHandler should ignore invalid schemes without crashing", true);
                } catch (Exception e) {
                    throw new AssertionError("IntentHandler crashed on invalid scheme", e);
                }
            });
        }
    }

    @Test
    public void processViewIntentWithFileSchemeAndLimeExtension_doesNotCrash() {
        try (ActivityScenario<LIMESettings> scenario = ActivityScenario.launch(LIMESettings.class)) {
            scenario.onActivity(activity -> {
                try {
                    File tmpDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    File tmp = new File(tmpDir == null ? activity.getCacheDir() : tmpDir, "shared.lime");
                    try (FileOutputStream fos = new FileOutputStream(tmp)) {
                        fos.write("c\td\n".getBytes(StandardCharsets.UTF_8));
                    }

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.fromFile(tmp), "text/plain");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    IntentHandler handler = new IntentHandler(activity, activity.getSetupImController());
                    handler.processIntent(intent);

                    assertTrue("IntentHandler should process file:// .lime without crashing", true);
                } catch (Exception e) {
                    throw new AssertionError("IntentHandler crashed processing file:// .lime", e);
                }
            });
        }
    }
}
