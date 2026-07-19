package org.limeime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.limeime.global.PreferenceBackupAdapter;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.ObjectOutputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class PreferenceBackupAdapterTest {
    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().commit();
    }

    @After
    public void tearDown() {
        prefs.edit().clear().commit();
    }

    @Test
    public void exportManifestBacksUpFullPrefsTableSetWithCanonicalTypes() throws Exception {
        Map<String, Object> expected = fullAndroidPrefsTableFixture();
        seedPrefs(expected);
        prefs.edit().putString("PAYMENT_FLAG", "do-not-export").commit();

        JSONObject manifest = PreferenceBackupAdapter.exportManifest(context);
        JSONObject values = manifest.getJSONObject("preferences");

        assertEquals(1, manifest.getInt("schema"));
        assertEquals("android", manifest.getString("sourcePlatform"));
        assertEquals("Manifest must contain exactly the full Android PREFS_TABLE set seeded by this test",
                expected.size(), values.length());
        assertManifestValues(values, expected);
        assertFalse(values.has("PAYMENT_FLAG"));
    }

    @Test
    public void restoreManifestConvertsCanonicalTypesToAndroidStorage() throws Exception {
        JSONObject values = new JSONObject()
                .put("keyboard_theme", 4)
                .put("keyboard_size", "1")
                .put("show_arrow_key", 2)
                .put("vibrate_level", 80)
                .put("han_convert_option", 2)
                .put("custom_im_reverselookup", "dayi")
                .put("auto_commit", 3)
                .put("smart_chinese_input", false)
                .put("physical_keyboard_sort", true)
                .put("unknown_pref", "ignored");
        JSONObject manifest = new JSONObject()
                .put("schema", 1)
                .put("sourcePlatform", "ios")
                .put("preferences", values);

        assertTrue(PreferenceBackupAdapter.restoreManifest(context, manifest));

        assertEquals("4", prefs.getString("keyboard_theme", null));
        assertEquals("1", prefs.getString("keyboard_size", null));
        assertEquals("2", prefs.getString("show_arrow_key", null));
        assertEquals("80", prefs.getString("vibrate_level", null));
        assertEquals("2", prefs.getString("han_convert_option", null));
        assertEquals("dayi", prefs.getString("custom_im_reverselookup", null));
        assertEquals("3", prefs.getString("auto_commit", null));
        assertFalse(prefs.getBoolean("smart_chinese_input", true));
        assertTrue(prefs.getBoolean("physical_keyboard_sort", false));
        assertFalse(prefs.contains("unknown_pref"));
    }

    @Test
    public void restoreIosStyleManifestOnAndroid() throws Exception {
        JSONObject values = new JSONObject()
                .put("keyboard_theme", 4)
                .put("keyboard_size", "1")
                .put("smart_chinese_input", false)
                .put("ios_only_future_key", "ignored");
        JSONObject manifest = new JSONObject()
                .put("schema", 1)
                .put("sourcePlatform", "ios")
                .put("preferences", values);

        assertTrue(PreferenceBackupAdapter.restoreManifest(context, manifest));

        assertEquals("4", prefs.getString("keyboard_theme", null));
        assertEquals("1", prefs.getString("keyboard_size", null));
        assertFalse(prefs.getBoolean("smart_chinese_input", true));
        assertFalse(prefs.contains("ios_only_future_key"));
    }

    @Test
    public void restoreLegacyAndroidBackupMigratesPhoneProfileEvenWhenDefaultsAlreadyExist() throws Exception {
        prefs.edit()
                .putString("phone_portrait_keyboard_mode", "0")
                .putBoolean("phone_landscape_split", false)
                .putLong("startup_config_version", 123L)
                .commit();
        JSONObject values = new JSONObject()
                .put("split_keyboard_mode", 1)
                .put("one_hand_mode", 0);
        JSONObject manifest = new JSONObject()
                .put("schema", 1)
                .put("sourcePlatform", "android")
                .put("preferences", values);

        assertTrue(PreferenceBackupAdapter.restoreManifest(context, manifest));

        assertEquals("1", prefs.getString("phone_portrait_keyboard_mode", null));
        assertTrue(prefs.getBoolean("phone_landscape_split", false));
        assertEquals("1", prefs.getString("split_keyboard_mode", null));
        assertTrue(prefs.getLong("startup_config_version", 0L) > 123L);
    }

    @Test
    public void restoreLegacyIosBackupPreservesOneHandButDoesNotInventPhoneSplit() throws Exception {
        prefs.edit()
                .putString("phone_portrait_keyboard_mode", "0")
                .putBoolean("phone_landscape_split", true)
                .commit();
        JSONObject values = new JSONObject()
                .put("split_keyboard_mode", 1)
                .put("one_hand_mode", 2);
        JSONObject manifest = new JSONObject()
                .put("schema", 1)
                .put("sourcePlatform", "ios")
                .put("preferences", values);

        assertTrue(PreferenceBackupAdapter.restoreManifest(context, manifest));

        assertEquals("3", prefs.getString("phone_portrait_keyboard_mode", null));
        assertFalse(prefs.getBoolean("phone_landscape_split", true));
        assertEquals("1", prefs.getString("split_keyboard_mode", null));
    }

    @Test
    public void restoreLegacyMixedGeometryOnTabletDoesNotDeriveDormantPhoneProfile() throws Exception {
        prefs.edit()
                .putString("phone_portrait_keyboard_mode", "3")
                .putBoolean("phone_landscape_split", false)
                .commit();
        JSONObject values = new JSONObject()
                .put("split_keyboard_mode", 1)
                .put("one_hand_mode", 0);
        JSONObject manifest = new JSONObject()
                .put("schema", PreferenceBackupAdapter.SCHEMA_VERSION)
                .put("sourcePlatform", "android")
                .put("preferences", values);
        Configuration tabletConfig = new Configuration(context.getResources().getConfiguration());
        tabletConfig.smallestScreenWidthDp = 600;
        Context tabletContext = context.createConfigurationContext(tabletConfig);

        assertTrue(PreferenceBackupAdapter.restoreManifest(tabletContext, manifest));

        assertEquals("3", prefs.getString("phone_portrait_keyboard_mode", null));
        assertFalse(prefs.getBoolean("phone_landscape_split", true));
        assertEquals("1", prefs.getString("split_keyboard_mode", null));
    }

    @Test
    public void restoreManifestRestoresEveryAndroidPrefsTableValue() throws Exception {
        Map<String, Object> expected = fullAndroidPrefsTableFixture();
        JSONObject values = new JSONObject();
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            values.put(entry.getKey(), entry.getValue());
        }
        values.put("unknown_pref", "ignored");
        JSONObject manifest = new JSONObject()
                .put("schema", 1)
                .put("sourcePlatform", "ios")
                .put("preferences", values);

        assertTrue(PreferenceBackupAdapter.restoreManifest(context, manifest));

        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            assertStoredValue(entry.getKey(), entry.getValue());
        }
        assertFalse(prefs.contains("unknown_pref"));
    }

    @Test
    public void restoreManifestIgnoresWrongTypesAndInvalidSchema() throws Exception {
        JSONObject values = new JSONObject()
                .put("keyboard_theme", "not-an-integer")
                .put("smart_chinese_input", "not-a-boolean");
        JSONObject manifest = new JSONObject()
                .put("schema", 1)
                .put("preferences", values);

        assertTrue(PreferenceBackupAdapter.restoreManifest(context, manifest));
        assertFalse(prefs.contains("keyboard_theme"));
        assertFalse(prefs.contains("smart_chinese_input"));

        JSONObject invalidSchema = new JSONObject()
                .put("schema", 99)
                .put("preferences", new JSONObject().put("keyboard_theme", 4));
        assertFalse(PreferenceBackupAdapter.restoreManifest(context, invalidSchema));
        assertFalse(prefs.contains("keyboard_theme"));
    }

    @Test
    public void restoreManifestRejectsOversizedInput() throws Exception {
        byte[] oversized = new byte[1024 * 1024 + 1];

        assertFalse(PreferenceBackupAdapter.restoreManifest(context, new ByteArrayInputStream(oversized)));
    }

    @Test
    public void legacyPreferenceRestoreSkipsPaymentFlagByKey() throws Exception {
        File backup = new File(context.getCacheDir(), "legacy_payment_flag_" + System.currentTimeMillis() + ".bak");
        Map<String, Object> legacyValues = new HashMap<>();
        legacyValues.put("keyboard_theme", "4");
        legacyValues.put("safe_string", "PAYMENT_FLAG");
        legacyValues.put("PAYMENT_FLAG", true);

        try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(backup))) {
            output.writeObject(legacyValues);
        }

        DBServer.getInstance(context).restoreDefaultSharedPreference(backup);

        assertEquals("4", prefs.getString("keyboard_theme", null));
        assertEquals("PAYMENT_FLAG", prefs.getString("safe_string", null));
        assertFalse(prefs.contains("PAYMENT_FLAG"));

        if (backup.exists()) {
            backup.delete();
        }
    }

    private Map<String, Object> fullAndroidPrefsTableFixture() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("keyboard_theme", 4);
        values.put("keyboard_size", "1");
        values.put("font_size", "2");
        values.put("number_row_in_english", false);
        values.put("show_arrow_key", 2);
        values.put("split_keyboard_mode", 1);
        values.put("one_hand_mode", 2);
        values.put("phone_portrait_keyboard_mode", 3);
        values.put("phone_landscape_split", true);
        values.put("numpad_anchor", 2);
        values.put("vibrate_on_keypress", false);
        values.put("vibrate_level", 80);
        values.put("sound_on_keypress", true);
        values.put("keypress_sound_volume", "0.25");
        values.put("smart_chinese_input", false);
        values.put("auto_chinese_symbol", true);
        values.put("candidate_switch", true);
        values.put("persistent_language_mode", true);
        values.put("enable_emoji_position", 3);
        values.put("similiar_list", 30);
        values.put("han_convert_option", 2);
        values.put("similiar_enable", false);
        values.put("candidate_suggestion", false);
        values.put("learn_phrase", false);
        values.put("learning_switch", false);
        values.put("english_dictionary_enable", false);
        values.put("auto_cap", false);
        values.put("custom_im_reverselookup", "dayi");
        values.put("cj_im_reverselookup", "phonetic");
        values.put("scj_im_reverselookup", "cj");
        values.put("cj5_im_reverselookup", "scj");
        values.put("ecj_im_reverselookup", "cj5");
        values.put("dayi_im_reverselookup", "bpmf");
        values.put("bpmf_im_reverselookup", "dayi");
        values.put("phonetic_im_reverselookup", "custom");
        values.put("ez_im_reverselookup", "array");
        values.put("array_im_reverselookup", "array10");
        values.put("array10_im_reverselookup", "ez");
        values.put("wb_im_reverselookup", "hs");
        values.put("hs_im_reverselookup", "pinyin");
        values.put("pinyin_im_reverselookup", "none");
        values.put("phonetic_keyboard_type", "standard");
        values.put("auto_commit", 3);
        values.put("accept_number_index", true);
        values.put("accept_symbol_index", true);
        values.put("backup_on_delete_phonetic", false);
        values.put("restore_on_import_phonetic", false);
        values.put("hide_software_keyboard_typing_with_physical", false);
        values.put("switch_english_mode", true);
        values.put("switch_english_mode_shift", false);
        values.put("disable_physical_selkey", true);
        values.put("selkey_option", 2);
        values.put("english_dictionary_physical_keyboard", true);
        values.put("physical_keyboard_sort", true);
        return values;
    }

    private void seedPrefs(Map<String, Object> values) {
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof Integer && isAndroidStringBackedInteger(entry.getKey())) {
                editor.putString(entry.getKey(), String.valueOf(value));
            } else if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            }
        }
        editor.commit();
    }

    private void assertManifestValues(JSONObject actual, Map<String, Object> expected) throws Exception {
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            String key = entry.getKey();
            Object expectedValue = entry.getValue();
            if (expectedValue instanceof Boolean) {
                assertEquals(key + " should be backed up as a boolean", expectedValue, actual.getBoolean(key));
            } else if (expectedValue instanceof Integer) {
                assertEquals(key + " should be backed up as an integer", expectedValue, actual.getInt(key));
            } else if (expectedValue instanceof String) {
                assertEquals(key + " should be backed up as a string", expectedValue, actual.getString(key));
            }
        }
    }

    private void assertStoredValue(String key, Object expectedValue) {
        if (expectedValue instanceof Boolean) {
            assertEquals(key + " should restore as a boolean", expectedValue, prefs.getBoolean(key, !((Boolean) expectedValue)));
        } else if (expectedValue instanceof Integer) {
            assertEquals(key + " should restore as Android string-backed integer",
                    String.valueOf(expectedValue), prefs.getString(key, null));
        } else if (expectedValue instanceof String) {
            assertEquals(key + " should restore as a string", expectedValue, prefs.getString(key, null));
        }
    }

    private boolean isAndroidStringBackedInteger(String key) {
        return Arrays.asList(
                "keyboard_theme",
                "show_arrow_key",
                "split_keyboard_mode",
                "one_hand_mode",
                "phone_portrait_keyboard_mode",
                "numpad_anchor",
                "vibrate_level",
                "enable_emoji_position",
                "similiar_list",
                "han_convert_option",
                "auto_commit",
                "selkey_option").contains(key);
    }
}
