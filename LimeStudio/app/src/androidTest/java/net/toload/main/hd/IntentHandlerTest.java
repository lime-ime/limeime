package net.toload.main.hd;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import net.toload.main.hd.ui.MainActivity;
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
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
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
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
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
    public void processZipIntent_doesNotCrash() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    File tmp = new File(activity.getCacheDir(), "array.limedb");
                    try (FileOutputStream fos = new FileOutputStream(tmp)) {
                        fos.write(new byte[]{0x50, 0x4B, 0x03, 0x04}); // minimal zip signature
                    }

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.fromFile(tmp), "application/zip");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    IntentHandler handler = new IntentHandler(activity, activity.getSetupImController());
                    handler.processIntent(intent);

                    assertTrue("IntentHandler should process zip without crashing", true);
                } catch (Exception e) {
                    throw new AssertionError("IntentHandler crashed processing zip intent", e);
                }
            });
        }
    }

    @Test
    public void processViewIntentWithInvalidScheme_gracefullyFails() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
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
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
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
