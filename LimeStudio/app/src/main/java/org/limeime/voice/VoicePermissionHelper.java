package org.limeime.voice;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

public final class VoicePermissionHelper {
    public static final String PREF_RECORD_AUDIO_PERMISSION_PROMPTED =
            "voice_inline_permission_prompted";

    private VoicePermissionHelper() {
    }

    public static boolean hasRecordAudioPermission(Context context) {
        return context != null
                && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static VoicePermissionState getRecordAudioPermissionState(Activity activity) {
        if (activity == null) {
            return VoicePermissionState.NOT_REQUESTED;
        }
        if (hasRecordAudioPermission(activity)) {
            return VoicePermissionState.GRANTED;
        }
        boolean prompted = wasRecordAudioPermissionPrompted(activity);
        if (!prompted) {
            return VoicePermissionState.NOT_REQUESTED;
        }
        return ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.RECORD_AUDIO)
                ? VoicePermissionState.DENIED_CAN_ASK
                : VoicePermissionState.DENIED_DO_NOT_ASK_AGAIN;
    }

    public static VoicePermissionState getRecordAudioPermissionState(Fragment fragment) {
        if (fragment == null || fragment.getContext() == null) {
            return VoicePermissionState.NOT_REQUESTED;
        }
        Context context = fragment.requireContext();
        if (hasRecordAudioPermission(context)) {
            return VoicePermissionState.GRANTED;
        }
        boolean prompted = wasRecordAudioPermissionPrompted(context);
        if (!prompted) {
            return VoicePermissionState.NOT_REQUESTED;
        }
        return fragment.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                ? VoicePermissionState.DENIED_CAN_ASK
                : VoicePermissionState.DENIED_DO_NOT_ASK_AGAIN;
    }

    public static void markRecordAudioPermissionPrompted(Context context) {
        if (context == null) {
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(PREF_RECORD_AUDIO_PERMISSION_PROMPTED, true)
                .apply();
    }

    public static boolean wasRecordAudioPermissionPrompted(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREF_RECORD_AUDIO_PERMISSION_PROMPTED, false);
    }

    public static Intent createAppSettingsIntent(Context context) {
        String packageName = context == null ? "" : context.getPackageName();
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + packageName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public static void openAppSettings(Context context) {
        if (context == null) {
            return;
        }
        context.startActivity(createAppSettingsIntent(context));
    }
}
