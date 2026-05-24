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

package net.toload.main.hd.ui.view;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.voice.VoicePermissionHelper;
import net.toload.main.hd.voice.VoicePermissionState;

/**
 * Activation-guide and About card fragment for the 設定 (Setup) tab.
 *
 * <p>Displays current IME activation status, step-by-step setup instructions,
 * buttons to open system IME settings / picker, and an About card with version,
 * license, and source-code link.
 */
public class SetupFragment extends Fragment {

    private static final String TAG = "SetupFragment";

    private Activity activity;
    private BroadcastReceiver imeChangeReceiver;
    private final ActivityResultLauncher<String> recordAudioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> refreshVoicePermissionStatus());

    private MaterialCardView statusCard;
    private TextView statusText;
    private ImageView statusIcon;
    private TextView setupHeading;
    private TextView setupStep1Description;
    private TextView setupStep2Description;
    private MaterialButton btnSystemSettings;
    private MaterialButton btnImePicker;
    private MaterialCardView voicePermissionCard;
    private ImageView voicePermissionIcon;
    private TextView voicePermissionTitle;
    private TextView voicePermissionDetail;
    private MaterialButton voicePermissionButton;

    public static SetupFragment newInstance() {
        return new SetupFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        activity = getActivity();
        View rootView = inflater.inflate(R.layout.fragment_setup, container, false);
        NestedScrollView scrollView = rootView.findViewById(R.id.setup_scroll);
        if (scrollView != null) {
            ScrollableTabHelper.applyToNestedScrollView(activity, scrollView);
        }

        statusCard = rootView.findViewById(R.id.statusCard);
        statusText = rootView.findViewById(R.id.statusText);
        statusIcon = rootView.findViewById(R.id.statusIcon);
        setupHeading = rootView.findViewById(R.id.setupHeading);
        setupStep1Description = rootView.findViewById(R.id.setupStep1Description);
        setupStep2Description = rootView.findViewById(R.id.setupStep2Description);
        btnSystemSettings = rootView.findViewById(R.id.btnSetupImSystemSetting);
        btnImePicker = rootView.findViewById(R.id.btnSetupImSystemIMPicker);
        voicePermissionCard = rootView.findViewById(R.id.voicePermissionCard);
        voicePermissionIcon = rootView.findViewById(R.id.voicePermissionIcon);
        voicePermissionTitle = rootView.findViewById(R.id.voicePermissionTitle);
        voicePermissionDetail = rootView.findViewById(R.id.voicePermissionDetail);
        voicePermissionButton = rootView.findViewById(R.id.voicePermissionButton);

        btnSystemSettings.setOnClickListener(v ->
                LIMEUtilities.showInputMethodSettingsPage(requireActivity().getApplicationContext()));
        btnImePicker.setOnClickListener(v ->
                LIMEUtilities.showInputMethodPicker(requireActivity().getApplicationContext()));
        if (voicePermissionButton != null) {
            voicePermissionButton.setOnClickListener(v -> openVoicePermissionSettings());
        }

        // Version in about card
        try {
            PackageInfo pInfo = requireActivity().getPackageManager()
                    .getPackageInfo(requireActivity().getPackageName(), 0);
            long code = PackageInfoCompat.getLongVersionCode(pInfo);
            ((TextView) rootView.findViewById(R.id.txtVersion))
                    .setText(getString(R.string.version_format, pInfo.versionName, code));
        } catch (Exception e) {
            Log.w(TAG, "Could not read version", e);
        }

        // License and GitHub link taps
        TextView txtLicense = rootView.findViewById(R.id.txtLicenseUrl);
        if (txtLicense != null) {
            txtLicense.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.url_license_limeime)));
                startActivity(intent);
            });
        }

        TextView txtGithub = rootView.findViewById(R.id.txtGithubUrl);
        if (txtGithub != null) {
            txtGithub.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.url_github_limeime)));
                startActivity(intent);
            });
        }

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        activity = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        registerImeReceiver();
        refreshStatus();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterImeReceiver();
    }

    private void refreshStatus() {
        if (!isAdded() || activity == null) return;
        Context ctx = activity.getApplicationContext();
        boolean enabled = LIMEUtilities.isLIMEEnabled(ctx);
        boolean active = LIMEUtilities.isLIMEActive(ctx);
        refreshVoicePermissionStatus();

        // Neutral subtle background; the state color is carried by icon + text (iOS parity)
        statusCard.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.setup_status_bg));

        if (enabled && active) {
            int fg = ContextCompat.getColor(activity, R.color.setup_status_fg_green);
            statusIcon.setImageResource(R.drawable.ic_status_check);
            statusIcon.setColorFilter(fg);
            statusText.setTextColor(fg);
            statusText.setText(R.string.setup_status_active);
            setupHeading.setVisibility(View.GONE);
            setupStep1Description.setVisibility(View.GONE);
            setupStep2Description.setVisibility(View.GONE);
            btnSystemSettings.setVisibility(View.GONE);
            btnImePicker.setVisibility(View.GONE);
        } else if (enabled) {
            int fg = ContextCompat.getColor(activity, R.color.setup_status_fg_yellow);
            statusIcon.setImageResource(R.drawable.ic_status_warning);
            statusIcon.setColorFilter(fg);
            statusText.setTextColor(fg);
            statusText.setText(R.string.setup_status_enabled_not_active);
            setupHeading.setVisibility(View.VISIBLE);
            setupStep1Description.setVisibility(View.GONE);
            setupStep2Description.setVisibility(View.VISIBLE);
            btnSystemSettings.setVisibility(View.GONE);
            btnImePicker.setVisibility(View.VISIBLE);
        } else {
            int fg = ContextCompat.getColor(activity, R.color.setup_status_fg_red);
            statusIcon.setImageResource(R.drawable.ic_status_error);
            statusIcon.setColorFilter(fg);
            statusText.setTextColor(fg);
            statusText.setText(R.string.setup_status_not_enabled);
            setupHeading.setVisibility(View.VISIBLE);
            setupStep1Description.setVisibility(View.VISIBLE);
            setupStep2Description.setVisibility(View.GONE);
            btnSystemSettings.setVisibility(View.VISIBLE);
            btnImePicker.setVisibility(View.GONE);
        }
    }

    private void refreshVoicePermissionStatus() {
        if (voicePermissionCard == null || activity == null || !isAdded()) {
            return;
        }
        if (!getResources().getBoolean(R.bool.inline_dictation_feature_enabled)) {
            voicePermissionCard.setVisibility(View.GONE);
            return;
        }

        voicePermissionCard.setVisibility(View.VISIBLE);
        VoicePermissionState state = VoicePermissionHelper.getRecordAudioPermissionState(this);
        int fg;
        switch (state) {
            case GRANTED:
                fg = ContextCompat.getColor(activity, R.color.setup_status_fg_green);
                voicePermissionIcon.setImageResource(R.drawable.ic_status_check);
                voicePermissionTitle.setText(R.string.setup_voice_permission_title_granted);
                voicePermissionDetail.setText(R.string.setup_voice_permission_granted);
                voicePermissionDetail.setVisibility(View.VISIBLE);
                voicePermissionButton.setVisibility(View.GONE);
                break;
            case DENIED_DO_NOT_ASK_AGAIN:
                fg = ContextCompat.getColor(activity, R.color.setup_status_fg_yellow);
                voicePermissionIcon.setImageResource(R.drawable.ic_status_warning);
                voicePermissionTitle.setText(R.string.setup_voice_permission_title_settings);
                voicePermissionDetail.setText(R.string.setup_voice_permission_denied_permanently);
                voicePermissionDetail.setVisibility(View.VISIBLE);
                voicePermissionButton.setText(R.string.setup_voice_permission_open_settings);
                voicePermissionButton.setVisibility(View.VISIBLE);
                break;
            case DENIED_CAN_ASK:
                fg = ContextCompat.getColor(activity, R.color.setup_status_fg_red);
                voicePermissionIcon.setImageResource(R.drawable.ic_status_error);
                voicePermissionTitle.setText(R.string.setup_voice_permission_title_request);
                voicePermissionDetail.setText(R.string.setup_voice_permission_denied_once);
                voicePermissionDetail.setVisibility(View.VISIBLE);
                voicePermissionButton.setText(R.string.setup_voice_permission_request);
                voicePermissionButton.setVisibility(View.VISIBLE);
                break;
            case NOT_REQUESTED:
            default:
                fg = ContextCompat.getColor(activity, R.color.setup_status_fg_red);
                voicePermissionIcon.setImageResource(R.drawable.ic_status_error);
                voicePermissionTitle.setText(R.string.setup_voice_permission_title_request);
                voicePermissionDetail.setText(R.string.setup_voice_permission_not_granted);
                voicePermissionDetail.setVisibility(View.VISIBLE);
                voicePermissionButton.setText(R.string.setup_voice_permission_request);
                voicePermissionButton.setVisibility(View.VISIBLE);
                break;
        }
        voicePermissionIcon.setColorFilter(fg);
        voicePermissionTitle.setTextColor(fg);
    }

    private void openVoicePermissionSettings() {
        if (!isAdded() || activity == null) {
            return;
        }
        VoicePermissionState state = VoicePermissionHelper.getRecordAudioPermissionState(this);
        VoicePermissionHelper.markRecordAudioPermissionPrompted(activity);
        if (state == VoicePermissionState.NOT_REQUESTED
                || state == VoicePermissionState.DENIED_CAN_ASK) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        Toast.makeText(
                activity,
                R.string.setup_voice_permission_settings_hint,
                Toast.LENGTH_LONG).show();
        VoicePermissionHelper.openAppSettings(activity);
    }

    private void registerImeReceiver() {
        if (imeChangeReceiver != null || activity == null) return;
        imeChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshStatus();
            }
        };
        IntentFilter filter = new IntentFilter("android.intent.action.INPUT_METHOD_CHANGED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(imeChangeReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            activity.registerReceiver(imeChangeReceiver, filter);
        }
    }

    private void unregisterImeReceiver() {
        if (imeChangeReceiver != null && activity != null) {
            try {
                activity.unregisterReceiver(imeChangeReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            imeChangeReceiver = null;
        }
    }
}
