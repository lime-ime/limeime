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

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.ArrayList;
import java.util.Locale;
import androidx.core.os.ConfigurationCompat;

/**
 * Helper Activity to launch RecognizerIntent and return results.
 * <p>
 * API-level considerations for voice input:
 * <p>
 *   API < 33 (Android 12L and below): getParcelableExtra(String) used
 *       (non-typed, deprecated in API 33); broadcasts delivered without restrictions;
 *       InputMethodService can launch activities directly.
 *   API 33 (Android 13 / Tiramisu): getParcelableExtra(String, Class) introduced
 *       (type-safe variant); runtime permission POST_NOTIFICATIONS required for foreground
 *       services.
 *   API 34 (Android 14 / UpsideDownCake): implicit broadcast delivery restricted
 *       to system apps; setPackage() required on broadcast intents to ensure delivery;
 *       background activity launch restrictions tightened.
 *   API 35+ (Android 15+): InputMethodService cannot launch activities directly;
 *       this helper Activity is required as a trampoline to start RecognizerIntent; static
 *       field (sPendingVoiceText) used as primary result delivery mechanism to avoid broadcast
 *       timing issues; broadcast sent as backup.
 */

public class VoiceInputActivity extends ComponentActivity {
    private static final String TAG = "VoiceInputActivity";
    public static final String ACTION_VOICE_RESULT = "net.toload.main.hd.VOICE_INPUT_RESULT";
    public static final String EXTRA_RECOGNIZED_TEXT = "recognized_text";
    public static final String EXTRA_VOICE_INTENT = "voice_intent";

    /**
     * Static pending text set BEFORE finish() so LIMEService can consume it
     * in onStartInputView() without relying on broadcast delivery timing.
     */
    private static volatile String sPendingVoiceText = null;

    /**
     * Consume and return any pending voice text.
     * Returns the text and clears it atomically.
     */
    public static String consumePendingVoiceText() {
        String text = sPendingVoiceText;
        sPendingVoiceText = null;
        return text;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Make activity transparent
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // API 30+: FLAG_FULLSCREEN is deprecated; recommended to use
        // WindowInsetsController to control system bars instead.
        // API < 30: FLAG_FULLSCREEN is the standard way to hide the status bar.
        int flagFullscreen = WindowManager.LayoutParams.FLAG_FULLSCREEN;
        getWindow().setFlags(flagFullscreen, flagFullscreen);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        Log.i(TAG, "onCreate(): Starting voice input on API level " + Build.VERSION.SDK_INT);
        
        // Register ActivityResultLauncher for voice input
        ActivityResultLauncher<Intent> voiceInputLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handleVoiceInputResult);
        
        // Get the voiceIntent passed from LIMEService, or create a default one.
        // API 33+: use type-safe getParcelableExtra(String, Class) to avoid unchecked cast warnings.
        // API < 33: use deprecated getParcelableExtra(String) which returns raw Parcelable.
        Intent voiceIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+ (Tiramisu): type-safe parcelable extraction
            voiceIntent = getIntent().getParcelableExtra(EXTRA_VOICE_INTENT, Intent.class);
        } else {
            // API < 33: deprecated untyped parcelable extraction
            voiceIntent = getIntent().getParcelableExtra(EXTRA_VOICE_INTENT);
        }
        if (voiceIntent == null) {
            Log.w(TAG, "onCreate(): voiceIntent is NULL, using fallback with system locale");
            // Fallback: create a basic voiceIntent if not provided - use system locale
            voiceIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            voiceIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            // Use ConfigurationCompat to retrieve the top system locale without using
            // the deprecated Configuration.locale field.
            Locale systemLocale = ConfigurationCompat.getLocales(getResources().getConfiguration()).get(0);
            String languageTag;
            try {
                assert systemLocale != null;
                languageTag = systemLocale.toLanguageTag();
            } catch (NoSuchMethodError e) {
                languageTag = systemLocale.getLanguage();
                systemLocale.getCountry();
                if (!systemLocale.getCountry().isEmpty()) {
                    languageTag += "-" + systemLocale.getCountry();
                }
            }
            voiceIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag);
            //voiceIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now");
            voiceIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        } else {
            String language = voiceIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE);
            Log.i(TAG, "onCreate(): Received voiceIntent from LIMEService with language: " + language);
        }
        
        // Check if RecognizerIntent is available
        android.content.ComponentName componentName = voiceIntent.resolveActivity(getPackageManager());
        if (componentName == null) {
            Log.e(TAG, "onCreate(): RecognizerIntent not available on this device");
            android.widget.Toast.makeText(this, "Voice recognition not available", android.widget.Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        try {
            voiceInputLauncher.launch(voiceIntent);
            Log.i(TAG, "onCreate(): Launched RecognizerIntent: " + componentName.getPackageName() + "/" + componentName.getClassName());
        } catch (android.content.ActivityNotFoundException e) {
            Log.e(TAG, "onCreate(): ActivityNotFoundException launching RecognizerIntent: " + e.getMessage(), e);
            android.widget.Toast.makeText(this, "Voice recognition activity not found", android.widget.Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Log.e(TAG, "onCreate(): Failed to launch RecognizerIntent: " + e.getMessage(), e);
            android.widget.Toast.makeText(this, "Failed to start voice recognition: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    /**
     * Handle the result from voice input activity using Activity Result API.
     * Sets recognized text in a static field BEFORE finishing so LIMEService can
     * consume it reliably in onStartInputView(), then also sends a broadcast as backup.
     */
    private void handleVoiceInputResult(ActivityResult result) {
        String recognizedText = null;
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Intent data = result.getData();
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                recognizedText = results.get(0);
                Log.i(TAG, "handleVoiceInputResult(): Recognized text: '" + recognizedText + "'");
            } else {
                Log.w(TAG, "handleVoiceInputResult(): No results in data");
            }
        } else {
            Log.w(TAG, "handleVoiceInputResult(): Voice recognition cancelled or failed, resultCode: " + result.getResultCode());
        }

        // Store text in static field BEFORE finish() so LIMEService can pick it up
        // in onStartInputView() without relying on broadcast timing.
        if (recognizedText != null) {
            sPendingVoiceText = recognizedText;
            Log.i(TAG, "handleVoiceInputResult(): Stored pending voice text in static field");
        }

        // Finish so the window closes and the text field regains input focus.
        finish();

        // API 34+: implicit broadcast delivery restricted; setPackage() is required
        // in sendVoiceResultBroadcast() to ensure the intent reaches LIMEService.
        // API < 34: broadcast delivered without restriction, but setPackage() is still safe.
        // Note: this broadcast is a backup mechanism; the primary delivery is via
        // the static sPendingVoiceText field consumed in onStartInputView().
        if (recognizedText != null) {
            final String textToCommit = recognizedText;
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> sendVoiceResultBroadcast(textToCommit), 300);
        }
    }

    /**
     * Send voice input result broadcast to LIMEService as a backup mechanism.
     * Uses getApplicationContext() because this Activity may already be destroyed
     * after finish() was called.
     * <p>
     * API 34+: implicit broadcasts are restricted; setPackage() makes this an
     *          explicit-package broadcast so it can still be delivered.
     * API < 34: setPackage() is optional but harmless; included for consistency.
     */
    private void sendVoiceResultBroadcast(String recognizedText) {
        try {
            Intent resultIntent = new Intent(ACTION_VOICE_RESULT);
            // API 34+: required to set package for broadcast delivery to non-system apps
            resultIntent.setPackage(getPackageName());
            resultIntent.putExtra(EXTRA_RECOGNIZED_TEXT, recognizedText);

            // Use ApplicationContext to ensure broadcast works after Activity is destroyed
            getApplicationContext().sendBroadcast(resultIntent);
            Log.i(TAG, "sendVoiceResultBroadcast(): Sent broadcast via ApplicationContext");
        } catch (Exception e) {
            // If broadcast fails, the static field (sPendingVoiceText) is the primary
            // mechanism and will be consumed by LIMEService in onStartInputView().
            Log.w(TAG, "sendVoiceResultBroadcast(): Broadcast failed (static field is primary): " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy()");
    }
}


