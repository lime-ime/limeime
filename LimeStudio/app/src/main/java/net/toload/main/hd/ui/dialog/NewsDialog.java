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

package net.toload.main.hd.ui.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment ;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;

import net.toload.main.hd.global.LIME;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEPreferenceManager;

/**
 * Dialog fragment that displays news or HTML content stored in preferences.
 *
 * <p>Loads HTML content from preferences and displays it in a {@link android.webkit.WebView}.
 * Provides a simple dismiss button.
 */
public class NewsDialog extends DialogFragment {

	//Activity activity;
	View view;

	Button btnHelpDialog;


    public static NewsDialog newInstance() {
		NewsDialog btd = new NewsDialog();
		btd.setCancelable(true);
		return btd;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
	}

	@Override
	public void onCancel(@NonNull DialogInterface dialog) {
		super.onCancel(dialog);
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		this.setCancelable(false);
	}



	@Override
	public void onStart() {
		super.onStart();
		Dialog dialog = getDialog();
		if (dialog != null) {
			dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		}
	}

	@Override
	public void onResume() {
		super.onResume();

        assert getDialog() != null;
        getDialog().setOnKeyListener((dialog, keyCode, event) -> {
            if ((keyCode == android.view.KeyEvent.KEYCODE_BACK)) {
                // To dismiss the fragment when the back-button is pressed.
                dismiss();
                return true;
            }
            // Otherwise, do nothing else
            else return false;
        });
	}

	public void cancelDialog(){
		this.dismiss();
	}

	@SuppressLint("SetJavaScriptEnabled")
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {

        assert getDialog() != null;
        getDialog().getWindow().setTitle(getResources().getString(R.string.action_news));

        LIMEPreferenceManager mLIMEPref = new LIMEPreferenceManager(getActivity());

		view = inflater.inflate(R.layout.fragment_dialog_news, container, false);

		String html_value = mLIMEPref.getParameterString(LIME.LIME_NEWS_CONTENT, "");
		if(!html_value.isEmpty()){
			WebView newsContentArea = view.findViewById(R.id.newsContentArea);
			newsContentArea.getSettings().setJavaScriptEnabled(true);
			newsContentArea.loadData(html_value, "text/html", "UTF-8");
		}

		btnHelpDialog = view.findViewById(R.id.btnNewsDialog);
		btnHelpDialog.setOnClickListener(v -> onNewsButtonClick());

		return view;

	}

	private void onNewsButtonClick() {
		dismiss();
	}

}
