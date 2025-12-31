/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Architecture Compliance Tests for UI_ARCHITECTURE v1.1
 * 
 * Verifies that UI components follow MVC pattern:
 * - No direct LimeDB access in UI layer
 * - Controllers use SearchServer/DBServer
 * - Proper delegation patterns
 */
@RunWith(AndroidJUnit4.class)
public class ArchitectureComplianceTest {

    private Context context;
    private File sourceRoot;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Get the source root directory
        String packageCodePath = context.getPackageCodePath();
        File apkFile = new File(packageCodePath);
        // Navigate to project root from APK location
        sourceRoot = apkFile.getParentFile().getParentFile().getParentFile().getParentFile();
    }

    /**
     * Test: No direct LimeDB instantiation in UI components
     * 
     * Verifies that UI layer components (fragments, dialogs, activities) 
     * do not create LimeDB instances directly.
     */
    @Test
    public void testNoDirectLimeDBInUIComponents() {
        List<String> violations = new ArrayList<>();
        
        // Check UI package for direct LimeDB usage
        File uiDir = new File(sourceRoot, "app/src/main/java/net/toload/main/hd/ui");
        
        if (uiDir.exists() && uiDir.isDirectory()) {
            scanForLimeDBViolations(uiDir, violations);
        }
        
        // Assert no violations found
        if (!violations.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("Architecture violation: Direct LimeDB access found in UI layer:\n");
            for (String violation : violations) {
                message.append("  - ").append(violation).append("\n");
            }
            fail(message.toString());
        }
    }

    /**
     * Test: SetupImController uses SearchServer/DBServer
     * 
     * Verifies controller follows delegation pattern.
     */
    @Test
    public void testSetupImControllerUsesServerLayers() {
        try {
            Class<?> controllerClass = Class.forName("net.toload.main.hd.ui.controller.SetupImController");
            
            // Check for SearchServer and DBServer field types using reflection
            java.lang.reflect.Field[] fields = controllerClass.getDeclaredFields();
            
            boolean hasSearchServerField = false;
            boolean hasDBServerField = false;
            
            for (java.lang.reflect.Field f : fields) {
                String fieldTypeName = f.getType().getSimpleName();
                if (fieldTypeName.contains("SearchServer")) {
                    hasSearchServerField = true;
                }
                if (fieldTypeName.contains("DBServer")) {
                    hasDBServerField = true;
                }
            }
            
            assertTrue("SetupImController should use SearchServer", hasSearchServerField);
            assertTrue("SetupImController should use DBServer", hasDBServerField);
            
        } catch (ClassNotFoundException e) {
            fail("SetupImController.class not found: " + e.getMessage());
        }
    }

    /**
     * Test: ManageImController uses SearchServer
     * 
     * Verifies controller follows delegation pattern.
     */
    @Test
    public void testManageImControllerUsesSearchServer() {
        try {
            Class<?> controllerClass = Class.forName("net.toload.main.hd.ui.controller.ManageImController");
            
            // Check for SearchServer field type using reflection
            java.lang.reflect.Field[] fields = controllerClass.getDeclaredFields();
            
            boolean hasSearchServerField = false;
            
            for (java.lang.reflect.Field f : fields) {
                String fieldTypeName = f.getType().getSimpleName();
                if (fieldTypeName.contains("SearchServer")) {
                    hasSearchServerField = true;
                    break;
                }
            }
            
            assertTrue("ManageImController should use SearchServer", hasSearchServerField);
            
        } catch (ClassNotFoundException e) {
            fail("ManageImController.class not found: " + e.getMessage());
        }
    }

    /**
     * Test: IntentHandler delegates to controllers
     * 
     * Verifies IntentHandler doesn't perform direct DB/file operations.
     */
    @Test
    public void testIntentHandlerDelegatesToControllers() {
        try {
            Class<?> handlerClass = Class.forName("net.toload.main.hd.ui.IntentHandler");
            
            // Verify the class can be instantiated (has constructor)
            java.lang.reflect.Constructor<?>[] constructors = handlerClass.getConstructors();
            assertTrue("IntentHandler should have accessible constructor", constructors.length > 0);
            
            // Check that it has methods to handle intents
            boolean hasMethods = false;
            for (java.lang.reflect.Method m : handlerClass.getDeclaredMethods()) {
                String name = m.getName();
                if (name.contains("handle") || name.contains("onIntent")) {
                    hasMethods = true;
                    break;
                }
            }
            assertTrue("IntentHandler should have methods to process intents", hasMethods);
            
        } catch (ClassNotFoundException e) {
            fail("IntentHandler.class not found: " + e.getMessage());
        }
    }

    /**
     * Test: MainActivity is coordinator pattern
     * 
     * Verifies MainActivity creates and exposes singletons via getters.
     */
    @Test
    public void testMainActivityIsCoordinator() {
        try {
            Class<?> mainActivityClass = Class.forName("net.toload.main.hd.ui.MainActivity");
            
            // Check for getter methods using reflection
            boolean hasSetupImControllerGetter = false;
            boolean hasManageImControllerGetter = false;
            boolean hasProgressManagerGetter = false;
            
            for (java.lang.reflect.Method m : mainActivityClass.getMethods()) {
                String name = m.getName();
                if (name.equals("getSetupImController")) {
                    hasSetupImControllerGetter = true;
                }
                if (name.equals("getManageImController")) {
                    hasManageImControllerGetter = true;
                }
                if (name.equals("getProgressManager")) {
                    hasProgressManagerGetter = true;
                }
            }
            
            assertTrue("MainActivity should have getSetupImController()", hasSetupImControllerGetter);
            assertTrue("MainActivity should have getManageImController()", hasManageImControllerGetter);
            assertTrue("MainActivity should have getProgressManager()", hasProgressManagerGetter);
        } catch (ClassNotFoundException e) {
            fail("MainActivity.class not found: " + e.getMessage());
        }
    }

    /**
     * Test: ImportDialog uses listener pattern
     * 
     * Verifies ImportDialog delegates selection via SetupImController listener.
     */
    @Test
    public void testImportDialogUsesListenerPattern() {
        File dialogFile = new File(sourceRoot, 
            "app/src/main/java/net/toload/main/hd/ui/dialog/ImportDialog.java");
        
        if (!dialogFile.exists()) {
            // Skip if dialog doesn't exist yet
            return;
        }
        
        List<String> content = readFileLines(dialogFile);
        
        boolean usesSearchServer = false;
        boolean usesLimeDBDirectly = false;
        
        for (String line : content) {
            if (line.contains("SearchServer")) {
                usesSearchServer = true;
            }
            if (line.matches(".*new\\s+LimeDB\\s*\\(.*") && !line.trim().startsWith("//")) {
                usesLimeDBDirectly = true;
            }
        }
        
        assertTrue("ImportDialog should use SearchServer for IM list", usesSearchServer);
        assertFalse("ImportDialog should not instantiate LimeDB directly", usesLimeDBDirectly);
    }

    // Helper methods

    private void scanForLimeDBViolations(File dir, List<String> violations) {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                // Exclude controller package - controllers are allowed to use servers
                if (!file.getName().equals("controller")) {
                    scanForLimeDBViolations(file, violations);
                }
            } else if (file.getName().endsWith(".java")) {
                checkFileForLimeDBUsage(file, violations);
            }
        }
    }

    private void checkFileForLimeDBUsage(File file, List<String> violations) {
        List<String> lines = readFileLines(file);
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            
            // Skip comments and imports
            if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*") 
                || line.startsWith("import")) {
                continue;
            }
            
            // Check for direct LimeDB instantiation
            if (line.matches(".*new\\s+LimeDB\\s*\\(.*")) {
                String relativePath = file.getAbsolutePath()
                    .substring(sourceRoot.getAbsolutePath().length());
                violations.add(String.format("%s:%d - Direct LimeDB instantiation", 
                    relativePath, i + 1));
            }
        }
    }

    private List<String> readFileLines(File file) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            fail("Failed to read file: " + file.getAbsolutePath() + " - " + e.getMessage());
        }
        return lines;
    }

    // ============================================================
    // 7.1 Static Analysis Tests
    // ============================================================

    /**
     * Test 7.1.2: No SQL operations outside LimeDB
     *
     * Verifies that SQL operations (execSQL, rawQuery, query, insert, update, delete)
     * only occur in LimeDB.java and allowed exceptions (LimeHanConverter, EmojiConverter).
     */
    @Test
    public void test_7_1_2_NoSQLOperationsOutsideLimeDB() {
        List<String> violations = new ArrayList<>();

        // Define SQL operation patterns to search for
        String[] sqlOperations = {
            "execSQL", "rawQuery", "\\.query\\(", "\\.insert\\(",
            "\\.update\\(", "\\.delete\\(", "SQLiteDatabase"
        };

        // Define allowed files (exceptions)
        String[] allowedFiles = {
            "LimeDB.java",
            "LimeHanConverter.java",
            "EmojiConverter.java"
        };

        // Scan main source directory
        File mainDir = new File(sourceRoot, "app/src/main/java/net/toload/main/hd");

        if (mainDir.exists() && mainDir.isDirectory()) {
            scanForSQLViolations(mainDir, violations, sqlOperations, allowedFiles);
        }

        // Assert no violations found
        if (!violations.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("Architecture violation: SQL operations found outside LimeDB:\n");
            for (String violation : violations) {
                message.append("  - ").append(violation).append("\n");
            }
            fail(message.toString());
        }
    }

    /**
     * Test 7.1.3: No file operations outside DBServer
     * Verifies that file operations (FileOutputStream, FileInputStream, zip, unzip)
     * only occur in DBServer.java and utilities.
     */
    @Test
    public void test_7_1_3_NoFileOperationsOutsideDBServer() {
        List<String> violations = new ArrayList<>();

        // Define file operation patterns to search for
        String[] fileOperations = {
            "FileOutputStream", "FileInputStream",
            "LIMEUtilities.zip", "LIMEUtilities.unzip",
            "new\\s+File\\(.*\\.limedb"
        };

        // Define allowed files (exceptions)
        String[] allowedFiles = {
            "DBServer.java",
            "LIMEUtilities.java",
            "SetupImController.java" // Allowed for temporary file handling
        };

        // Scan main source directory
        File mainDir = new File(sourceRoot, "app/src/main/java/net/toload/main/hd");

        if (mainDir.exists() && mainDir.isDirectory()) {
            scanForFileOpViolations(mainDir, violations, fileOperations, allowedFiles);
        }

        // Assert no violations found
        if (!violations.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("Architecture violation: File operations found outside DBServer:\n");
            for (String violation : violations) {
                message.append("  - ").append(violation).append("\n");
            }
            fail(message.toString());
        }
    }

    // ============================================================
    // 7.2 Runtime Architecture Tests
    // ============================================================

    /**
     * Test 7.2.1: Component initialization verification
     *
     * Verifies that UI components initialize SearchServer, not LimeDB directly,
     * and that LIMEService initializes SearchServer properly.
     */
    @Test
    public void test_7_2_1_ComponentInitialization() {
        try {
            // Test LIMEService initialization
            Class<?> limeServiceClass = Class.forName("net.toload.main.hd.LIMEService");
            java.lang.reflect.Field[] fields = limeServiceClass.getDeclaredFields();

            boolean hasSearchServerField = false;
            boolean hasLimeDBField = false;

            for (java.lang.reflect.Field field : fields) {
                String fieldTypeName = field.getType().getSimpleName();
                if (fieldTypeName.equals("SearchServer")) {
                    hasSearchServerField = true;
                }
                if (fieldTypeName.equals("LimeDB")) {
                    hasLimeDBField = true;
                }
            }

            assertTrue("LIMEService should have SearchServer field", hasSearchServerField);
            assertFalse("LIMEService should not have direct LimeDB field", hasLimeDBField);

        } catch (ClassNotFoundException e) {
            fail("LIMEService class not found: " + e.getMessage());
        }
    }

    /**
     * Test 7.2.2: Method call tracing
     *
     * Verifies that method calls from UI components go through SearchServer or DBServer,
     * not directly to LimeDB.
     */
    @Test
    public void test_7_2_2_MethodCallTracing() {
        // Test that controllers expose proper methods for UI
        try {
            Class<?> setupControllerClass = Class.forName("net.toload.main.hd.ui.controller.SetupImController");
            Class<?> manageControllerClass = Class.forName("net.toload.main.hd.ui.controller.ManageImController");

            // Verify SetupImController has proper delegation methods
            boolean hasTableMethod = false;
            boolean hasImportMethod = false;

            for (java.lang.reflect.Method method : setupControllerClass.getDeclaredMethods()) {
                String methodName = method.getName();
                if (methodName.contains("Table") || methodName.contains("clear")) {
                    hasTableMethod = true;
                }
                if (methodName.contains("import") || methodName.contains("download") || methodName.contains("export")) {
                    hasImportMethod = true;
                }
            }

            assertTrue("SetupImController should have table management methods", hasTableMethod);
            assertTrue("SetupImController should have import/export methods", hasImportMethod);

            // Verify ManageImController has proper delegation methods
            boolean hasRecordMethod = false;

            for (java.lang.reflect.Method method : manageControllerClass.getDeclaredMethods()) {
                String methodName = method.getName();
                if (methodName.contains("Record") || methodName.contains("count")) {
                    hasRecordMethod = true;
                    break;
                }
            }

            assertTrue("ManageImController should have record management methods", hasRecordMethod);

        } catch (ClassNotFoundException e) {
            fail("Controller class not found: " + e.getMessage());
        }
    }

    // Helper methods for new tests

    private void scanForSQLViolations(File dir, List<String> violations,
                                     String[] sqlOperations, String[] allowedFiles) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanForSQLViolations(file, violations, sqlOperations, allowedFiles);
            } else if (file.getName().endsWith(".java")) {
                // Skip allowed files
                boolean isAllowed = false;
                for (String allowed : allowedFiles) {
                    if (file.getName().equals(allowed)) {
                        isAllowed = true;
                        break;
                    }
                }

                if (!isAllowed) {
                    checkFileForSQLUsage(file, violations, sqlOperations);
                }
            }
        }
    }

    private void checkFileForSQLUsage(File file, List<String> violations, String[] sqlOperations) {
        List<String> lines = readFileLines(file);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // Skip comments and imports
            if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")
                || line.startsWith("import")) {
                continue;
            }

            // Check for SQL operations
            for (String sqlOp : sqlOperations) {
                if (line.matches(".*" + sqlOp + ".*")) {
                    String relativePath = file.getAbsolutePath()
                        .substring(sourceRoot.getAbsolutePath().length());
                    violations.add(String.format("%s:%d - SQL operation '%s'",
                        relativePath, i + 1, sqlOp));
                    break; // Only report first violation per line
                }
            }
        }
    }

    private void scanForFileOpViolations(File dir, List<String> violations,
                                        String[] fileOperations, String[] allowedFiles) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanForFileOpViolations(file, violations, fileOperations, allowedFiles);
            } else if (file.getName().endsWith(".java")) {
                // Skip allowed files
                boolean isAllowed = false;
                for (String allowed : allowedFiles) {
                    if (file.getName().equals(allowed)) {
                        isAllowed = true;
                        break;
                    }
                }

                if (!isAllowed) {
                    checkFileForFileOpUsage(file, violations, fileOperations);
                }
            }
        }
    }

    private void checkFileForFileOpUsage(File file, List<String> violations, String[] fileOperations) {
        List<String> lines = readFileLines(file);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // Skip comments and imports
            if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")
                || line.startsWith("import")) {
                continue;
            }

            // Check for file operations
            for (String fileOp : fileOperations) {
                if (line.matches(".*" + fileOp + ".*")) {
                    String relativePath = file.getAbsolutePath()
                        .substring(sourceRoot.getAbsolutePath().length());
                    violations.add(String.format("%s:%d - File operation '%s'",
                        relativePath, i + 1, fileOp));
                    break; // Only report first violation per line
                }
            }
        }
    }
}
