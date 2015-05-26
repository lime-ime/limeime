package net.toload.main.hd.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import net.toload.main.hd.R;

public class HelpDialog extends DialogFragment {

	Activity activity;
	View view;

	Button btnHelpDialog;


	public static HelpDialog newInstance() {
		HelpDialog btd = new HelpDialog();
		btd.setCancelable(true);
		return btd;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onAttach(Activity act) {
		super.onAttach(act);
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		super.onCancel(dialog);
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		this.setCancelable(false);
	}


	@Override
	public void onResume() {
		super.onResume();

		getDialog().setOnKeyListener(new DialogInterface.OnKeyListener() {
			@Override
			public boolean onKey(android.content.DialogInterface dialog,
								 int keyCode, android.view.KeyEvent event) {
				if ((keyCode == android.view.KeyEvent.KEYCODE_BACK)) {
					// To dismiss the fragment when the back-button is pressed.
					dismiss();
					return true;
				}
				// Otherwise, do nothing else
				else return false;
			}
		});
	}

	public void cancelDialog(){
		this.dismiss();
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {

		getDialog().getWindow().setTitle(getResources().getString(R.string.help_dialog_title));

		view = inflater.inflate(R.layout.fragment_dialog_help, container, false);

		btnHelpDialog = (Button) view.findViewById(R.id.btnHelpDialog);
		btnHelpDialog.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle icicle) {
		super.onSaveInstanceState(icicle);
	}

}
