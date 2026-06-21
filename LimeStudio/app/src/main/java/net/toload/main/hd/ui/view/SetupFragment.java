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
import net.toload.main.hd.data.ImConfig;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.ui.LIMESettings;
import net.toload.main.hd.ui.controller.ManageImController;
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
    private View voicePermissionCard;
    private View voicePermissionBanner;
    private ImageView voicePermissionIcon;
    private TextView voicePermissionTitle;
    private TextView voicePermissionDetail;
    private MaterialButton voicePermissionButton;

    // §4.3 Installed-IM status block.
    private View imStatusCard;
    private View imStatusBanner;
    private ImageView imStatusIcon;
    private TextView imStatusText;
    private MaterialButton imStatusButton;

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
        voicePermissionBanner = rootView.findViewById(R.id.voicePermissionBanner);
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

        // §4.3 Installed-IM status block.
        imStatusCard = rootView.findViewById(R.id.imStatusCard);
        imStatusBanner = rootView.findViewById(R.id.imStatusBanner);
        imStatusIcon = rootView.findViewById(R.id.imStatusIcon);
        imStatusText = rootView.findViewById(R.id.imStatusText);
        imStatusButton = rootView.findViewById(R.id.imStatusButton);
        if (imStatusButton != null) {
            imStatusButton.setOnClickListener(v -> openImTab());
        }

        // One-line copyright banner in the About footer:
        // "© LIME 萊姆輸入法 <versionName> - <year>". Same view/code path as
        // before — only the displayed copy changed to match the design footer.
        try {
            PackageInfo pInfo = requireActivity().getPackageManager()
                    .getPackageInfo(requireActivity().getPackageName(), 0);
            int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            ((TextView) rootView.findViewById(R.id.txtVersion))
                    .setText(getString(R.string.about_copyright_format, pInfo.versionName, year));
        } catch (Exception e) {
            Log.w(TAG, "Could not read version", e);
        }

        // Manual, License and GitHub link taps. The About chips are now
        // LinearLayouts (footer re-layout), so reference them as View — the
        // click behaviour and target URLs are unchanged.
        // 使用手冊 + 版權說明 open IN-PLACE (in-app Chrome Custom Tab) so the user
        // stays in the app; 原始碼 (GitHub) opens externally in the browser.
        View txtManual = rootView.findViewById(R.id.txtManualUrl);
        if (txtManual != null) {
            txtManual.setOnClickListener(v ->
                    openInAppTab(getString(R.string.url_manual_limeime)));
        }

        View txtLicense = rootView.findViewById(R.id.txtLicenseUrl);
        if (txtLicense != null) {
            txtLicense.setOnClickListener(v ->
                    openInAppTab(getString(R.string.url_license_limeime)));
        }

        View txtGithub = rootView.findViewById(R.id.txtGithubUrl);
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
        refreshImStatus();

        // Banner: status glyph + text in the ink colour over the matching
        // subtle status tint (design StatusBanner / color-status card).
        if (enabled && active) {
            int fg = ContextCompat.getColor(activity, R.color.setup_status_fg_green);
            statusCard.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.status_tint_green));
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
            statusCard.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.status_tint_yellow));
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
            statusCard.setCardBackgroundColor(ContextCompat.getColor(activity, R.color.status_tint_red));
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

    /**
     * §4.3 Installed-IM status. Mirrors the 輸入法 tab so Setup can surface a
     * problem and route the user to fix it:
     *   none     → danger  (no IM installed)     → 「安裝輸入法」
     *   disabled → warning (installed, all off)  → 「啟用輸入法」
     *   ok       → ≥1 installed & enabled        → card hidden (nothing to fix)
     * Reads the same IM config list as the IM tab. Identical states to iOS.
     */
    private void refreshImStatus() {
        if (imStatusCard == null || activity == null || !isAdded()) {
            return;
        }
        ManageImController ctrl = null;
        if (activity instanceof LIMESettings) {
            ctrl = ((LIMESettings) activity).getManageImController();
        }
        if (ctrl == null) {
            imStatusCard.setVisibility(View.GONE);
            return;
        }

        int installed = 0;
        int enabled = 0;
        for (ImConfig im : ctrl.getImConfigFullNameList()) {
            if (im == null || "emoji".equals(im.getCode())) continue; // skip internal emoji set
            installed++;
            if (!im.isDisable()) enabled++;
        }

        // The card is ALWAYS shown — it reports the IM state in all three cases
        // (none → red, disabled → orange, ok → green). Only none/disabled carry a CTA.
        imStatusCard.setVisibility(View.VISIBLE);
        if (installed == 0) {
            // none → danger (red), CTA 安裝輸入法
            int fg = ContextCompat.getColor(activity, R.color.setup_status_fg_red);
            imStatusBanner.setBackgroundColor(ContextCompat.getColor(activity, R.color.status_tint_red));
            imStatusIcon.setImageResource(R.drawable.ic_status_error);
            imStatusIcon.setColorFilter(fg);
            imStatusText.setTextColor(fg);
            imStatusText.setText(R.string.im_list_empty_title);
            imStatusButton.setVisibility(View.VISIBLE);
            imStatusButton.setText(R.string.im_list_empty_nudge);
        } else if (enabled == 0) {
            // disabled → warning (orange), CTA 啟用輸入法
            int fg = ContextCompat.getColor(activity, R.color.setup_status_fg_yellow);
            imStatusBanner.setBackgroundColor(ContextCompat.getColor(activity, R.color.status_tint_yellow));
            imStatusIcon.setImageResource(R.drawable.ic_status_warning);
            imStatusIcon.setColorFilter(fg);
            imStatusText.setTextColor(fg);
            imStatusText.setText(getString(R.string.setup_im_status_disabled, installed));
            imStatusButton.setVisibility(View.VISIBLE);
            imStatusButton.setText(R.string.setup_im_status_enable);
        } else {
            // ok → success (green), no CTA
            int fg = ContextCompat.getColor(activity, R.color.setup_status_fg_green);
            imStatusBanner.setBackgroundColor(ContextCompat.getColor(activity, R.color.status_tint_green));
            imStatusIcon.setImageResource(R.drawable.ic_status_check);
            imStatusIcon.setColorFilter(fg);
            imStatusText.setTextColor(fg);
            imStatusText.setText(getString(R.string.setup_im_status_ok, installed));
            imStatusButton.setVisibility(View.GONE);
        }
    }

    /** Route to the 輸入法 tab (install / enable an IM). */
    private void openImTab() {
        if (activity == null) return;
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav =
                activity.findViewById(R.id.main_bottom_nav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_im);
            return;
        }
        // Tablet: navigation rail instead of bottom nav.
        com.google.android.material.navigationrail.NavigationRailView navRail =
                activity.findViewById(R.id.main_nav_rail);
        if (navRail != null) {
            navRail.setSelectedItemId(R.id.nav_im);
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
        int tint;
        switch (state) {
            case GRANTED:
                fg = ContextCompat.getColor(activity, R.color.setup_status_fg_green);
                tint = ContextCompat.getColor(activity, R.color.status_tint_green);
                voicePermissionIcon.setImageResource(R.drawable.ic_status_check);
                voicePermissionTitle.setText(R.string.setup_voice_permission_title_granted);
                voicePermissionDetail.setText(R.string.setup_voice_permission_granted);
                voicePermissionDetail.setVisibility(View.VISIBLE);
                voicePermissionButton.setVisibility(View.GONE);
                break;
            case DENIED_DO_NOT_ASK_AGAIN:
                fg = ContextCompat.getColor(activity, R.color.setup_status_fg_yellow);
                tint = ContextCompat.getColor(activity, R.color.status_tint_yellow);
                voicePermissionIcon.setImageResource(R.drawable.ic_status_warning);
                voicePermissionTitle.setText(R.string.setup_voice_permission_title_settings);
                voicePermissionDetail.setText(R.string.setup_voice_permission_denied_permanently);
                voicePermissionDetail.setVisibility(View.VISIBLE);
                voicePermissionButton.setText(R.string.setup_voice_permission_open_settings);
                voicePermissionButton.setVisibility(View.VISIBLE);
                break;
            case DENIED_CAN_ASK:
                fg = ContextCompat.getColor(activity, R.color.setup_status_fg_red);
                tint = ContextCompat.getColor(activity, R.color.status_tint_red);
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
                tint = ContextCompat.getColor(activity, R.color.status_tint_red);
                voicePermissionIcon.setImageResource(R.drawable.ic_status_error);
                voicePermissionTitle.setText(R.string.setup_voice_permission_title_request);
                voicePermissionDetail.setText(R.string.setup_voice_permission_not_granted);
                voicePermissionDetail.setVisibility(View.VISIBLE);
                voicePermissionButton.setText(R.string.setup_voice_permission_request);
                voicePermissionButton.setVisibility(View.VISIBLE);
                break;
        }
        // Tint only the banner row (icon + title), not the description/button.
        voicePermissionBanner.setBackgroundColor(tint);
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

    /**
     * Open a URL in-place: an in-app Chrome Custom Tab (themed to the app accent)
     * so the user stays within LimeIME, instead of leaving for an external
     * browser. Falls back to a normal ACTION_VIEW if no Custom Tabs provider is
     * available.
     */
    private void openInAppTab(String url) {
        if (activity == null || url == null) return;
        Uri uri = Uri.parse(url);
        try {
            androidx.browser.customtabs.CustomTabsIntent intent =
                    new androidx.browser.customtabs.CustomTabsIntent.Builder()
                            .setShowTitle(true)
                            .build();
            intent.launchUrl(activity, uri);
        } catch (Exception e) {
            // No Custom Tabs provider (or any failure) — fall back to a browser.
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignored) {
            }
        }
    }
}
