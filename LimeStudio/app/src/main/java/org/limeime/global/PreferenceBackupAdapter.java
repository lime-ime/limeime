package org.limeime.global;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PreferenceBackupAdapter {
    public static final int SCHEMA_VERSION = 1;
    public static final String MANIFEST_PATH = "preferences/lime_prefs.json";
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;

    private enum Type {
        BOOLEAN,
        INTEGER_AS_STRING,
        STRING
    }

    private static final class Spec {
        final String key;
        final Type type;

        Spec(String key, Type type) {
            this.key = key;
            this.type = type;
        }
    }

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    static {
        add("keyboard_theme", Type.INTEGER_AS_STRING);
        add("keyboard_size", Type.STRING);
        add("font_size", Type.STRING);
        add("number_row_in_english", Type.BOOLEAN);
        add("show_arrow_key", Type.INTEGER_AS_STRING);
        add("split_keyboard_mode", Type.INTEGER_AS_STRING);
        add("vibrate_on_keypress", Type.BOOLEAN);
        add("vibrate_level", Type.INTEGER_AS_STRING);
        add("sound_on_keypress", Type.BOOLEAN);
        add("keypress_sound_volume", Type.STRING);
        add("smart_chinese_input", Type.BOOLEAN);
        add("auto_chinese_symbol", Type.BOOLEAN);
        add("candidate_switch", Type.BOOLEAN);
        add("persistent_language_mode", Type.BOOLEAN);
        add("enable_emoji_position", Type.INTEGER_AS_STRING);
        add("similiar_list", Type.INTEGER_AS_STRING);
        add("han_convert_option", Type.INTEGER_AS_STRING);
        add("similiar_enable", Type.BOOLEAN);
        add("candidate_suggestion", Type.BOOLEAN);
        add("learn_phrase", Type.BOOLEAN);
        add("learning_switch", Type.BOOLEAN);
        add("english_dictionary_enable", Type.BOOLEAN);
        add("auto_cap", Type.BOOLEAN);
        add("custom_im_reverselookup", Type.STRING);
        add("cj_im_reverselookup", Type.STRING);
        add("scj_im_reverselookup", Type.STRING);
        add("cj5_im_reverselookup", Type.STRING);
        add("ecj_im_reverselookup", Type.STRING);
        add("dayi_im_reverselookup", Type.STRING);
        add("bpmf_im_reverselookup", Type.STRING);
        add("phonetic_im_reverselookup", Type.STRING);
        add("ez_im_reverselookup", Type.STRING);
        add("array_im_reverselookup", Type.STRING);
        add("array10_im_reverselookup", Type.STRING);
        add("wb_im_reverselookup", Type.STRING);
        add("hs_im_reverselookup", Type.STRING);
        add("pinyin_im_reverselookup", Type.STRING);
        add("phonetic_keyboard_type", Type.STRING);
        add("auto_commit", Type.INTEGER_AS_STRING);
        add("accept_number_index", Type.BOOLEAN);
        add("accept_symbol_index", Type.BOOLEAN);
        add("hide_software_keyboard_typing_with_physical", Type.BOOLEAN);
        add("switch_english_mode", Type.BOOLEAN);
        add("switch_english_mode_shift", Type.BOOLEAN);
        add("disable_physical_selkey", Type.BOOLEAN);
        add("selkey_option", Type.INTEGER_AS_STRING);
        add("english_dictionary_physical_keyboard", Type.BOOLEAN);
        add("physical_keyboard_sort", Type.BOOLEAN);
    }

    private PreferenceBackupAdapter() {
    }

    private static void add(String key, Type type) {
        SPECS.put(key, new Spec(key, type));
    }

    public static JSONObject exportManifest(Context context) throws JSONException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Map<String, ?> storedValues = prefs.getAll();
        JSONObject values = new JSONObject();

        for (Spec spec : SPECS.values()) {
            if (!storedValues.containsKey(spec.key)) continue;
            putValue(values, spec, storedValues.get(spec.key));
        }
        for (Map.Entry<String, ?> entry : storedValues.entrySet()) {
            Spec dynamicSpec = dynamicSpecForKey(entry.getKey());
            if (dynamicSpec != null) {
                putValue(values, dynamicSpec, entry.getValue());
            }
        }

        return new JSONObject()
                .put("schema", SCHEMA_VERSION)
                .put("sourcePlatform", "android")
                .put("preferences", values);
    }

    public static byte[] exportManifestBytes(Context context) throws JSONException {
        return exportManifest(context).toString().getBytes(StandardCharsets.UTF_8);
    }

    public static boolean restoreManifest(Context context, JSONObject root) throws JSONException {
        if (root == null || root.optInt("schema", -1) != SCHEMA_VERSION) return false;

        JSONObject values = root.optJSONObject("preferences");
        if (values == null) return false;

        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        for (Spec spec : SPECS.values()) {
            if (!values.has(spec.key)) continue;
            restoreValue(editor, spec.key, spec.type, values.get(spec.key));
        }
        java.util.Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Spec dynamicSpec = dynamicSpecForKey(key);
            if (dynamicSpec != null) {
                restoreValue(editor, key, dynamicSpec.type, values.get(key));
            }
        }
        return editor.commit();
    }

    public static boolean restoreManifest(Context context, InputStream input) throws IOException, JSONException {
        if (input == null) return false;

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_MANIFEST_BYTES) {
                return false;
            }
            output.write(buffer, 0, count);
        }
        return restoreManifest(context, new JSONObject(output.toString(StandardCharsets.UTF_8.name())));
    }

    private static void putIntegerAsString(JSONObject values, String key, Object value) throws JSONException {
        if (isIntegralNumber(value)) {
            values.put(key, ((Number) value).intValue());
        } else if (value instanceof String) {
            try {
                values.put(key, Integer.parseInt((String) value));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static boolean isIntegralNumber(Object value) {
        return value instanceof Integer
                || value instanceof Long
                || value instanceof Short
                || value instanceof Byte;
    }

    private static Spec dynamicSpecForKey(String key) {
        if (key != null && (key.startsWith("backup_on_delete_") || key.startsWith("restore_on_import_"))) {
            return new Spec(key, Type.BOOLEAN);
        }
        return null;
    }

    private static void putValue(JSONObject values, Spec spec, Object value) throws JSONException {
        switch (spec.type) {
            case BOOLEAN:
                if (value instanceof Boolean) {
                    values.put(spec.key, value);
                }
                break;
            case INTEGER_AS_STRING:
                putIntegerAsString(values, spec.key, value);
                break;
            case STRING:
                if (value instanceof String) {
                    values.put(spec.key, value);
                }
                break;
        }
    }

    private static void restoreValue(SharedPreferences.Editor editor, String key, Type type, Object value) {
        switch (type) {
            case BOOLEAN:
                if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                }
                break;
            case INTEGER_AS_STRING:
                if (isIntegralNumber(value)) {
                    editor.putString(key, String.valueOf(((Number) value).intValue()));
                }
                break;
            case STRING:
                if (value instanceof String) {
                    editor.putString(key, (String) value);
                }
                break;
        }
    }
}
