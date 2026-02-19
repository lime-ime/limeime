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
        
        // Launch RecognizerIntent
        Intent voiceIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        voiceIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        voiceIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
        voiceIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now");
        voiceIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        
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
     * Sends recognized text back to LIMEService via broadcast with retry logic for Android 14+.
     */
    private void handleVoiceInputResult(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Intent data = result.getData();
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String recognizedText = results.get(0);
                Log.i(TAG, "handleVoiceInputResult(): Recognized text: '" + recognizedText + "'");
                
                // Send result back to LIMEService via broadcast
                sendVoiceResultBroadcast(recognizedText);
            } else {
                Log.w(TAG, "handleVoiceInputResult(): No results in data");
            }
        } else {
            Log.w(TAG, "handleVoiceInputResult(): Voice recognition cancelled or failed, resultCode: " + result.getResultCode());
        }
        finish();
    }

    /**
     * Send voice input result broadcast to LIMEService with retry logic.
     * On Android 14+ (API 34+), broadcasts may be restricted, so we retry with a delay.
     */
    private void sendVoiceResultBroadcast(String recognizedText) {
        Intent resultIntent = new Intent(ACTION_VOICE_RESULT);
        resultIntent.setPackage(getPackageName()); // Explicit package to target app only
        resultIntent.putExtra(EXTRA_RECOGNIZED_TEXT, recognizedText);
        
        // Send broadcast with retry on Android 14+ to handle delivery restrictions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ (API 34+): Send multiple times with slight delays to ensure delivery
            Log.i(TAG, "sendVoiceResultBroadcast(): Using multi-send strategy for Android 14+");
            
            sendBroadcast(resultIntent);
            Log.i(TAG, "sendVoiceResultBroadcast(): Sent broadcast (attempt 1)");
            
            // Send again after 50ms to ensure receiver gets it
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                sendBroadcast(resultIntent);
                Log.i(TAG, "sendVoiceResultBroadcast(): Sent broadcast (attempt 2 - delayed)");
            }, 50);
            
        } else {
            // Android 13 and earlier: Single send is sufficient
            Log.i(TAG, "sendVoiceResultBroadcast(): Using single-send for Android 13 and earlier");
            sendBroadcast(resultIntent);
            Log.i(TAG, "sendVoiceResultBroadcast(): Sent broadcast");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy()");
    }
}


