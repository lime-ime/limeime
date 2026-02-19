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
 * Helper Activity to launch RecognizerIntent and return results
 * This is needed because InputMethodService cannot launch activities on API 35+
 * 
 * Note: On Android 14+ (API 34+), broadcast delivery may be restricted to system apps.
 * This class manages the broadcast to ensure it reaches LIMEService's receiver.
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
        // FLAG_FULLSCREEN is deprecated in API 30+, but necessary for older APIs
        @SuppressWarnings("deprecation")
        int flagFullscreen = WindowManager.LayoutParams.FLAG_FULLSCREEN;
        getWindow().setFlags(flagFullscreen, flagFullscreen);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        Log.i(TAG, "onCreate(): Starting voice input on API level " + Build.VERSION.SDK_INT);
        
        // Register ActivityResultLauncher for voice input
        ActivityResultLauncher<Intent> voiceInputLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handleVoiceInputResult);
        
        // Get the voiceIntent passed from LIMEService, or create a default one
        Intent voiceIntent = getIntent().getParcelableExtra(EXTRA_VOICE_INTENT, Intent.class);
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
                languageTag = systemLocale.toLanguageTag();
            } catch (NoSuchMethodError e) {
                languageTag = systemLocale.getLanguage();
                if (systemLocale.getCountry() != null && !systemLocale.getCountry().isEmpty()) {
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

        // Also send broadcast as a backup mechanism, using ApplicationContext
        // since this Activity's context may be invalid after finish().
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
     */
    private void sendVoiceResultBroadcast(String recognizedText) {
        try {
            Intent resultIntent = new Intent(ACTION_VOICE_RESULT);
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


