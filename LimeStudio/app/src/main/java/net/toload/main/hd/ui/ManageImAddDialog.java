package net.toload.main.hd.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import net.toload.main.hd.R;

public class ManageImAddDialog extends DialogFragment {

	Activity activity;
	View view;

	//Button btnQuizExitConfirm;
	//Button btnQuizExitCancel;
	
	ManageImHandler handler;

	Button btnManageImWordCancel;
	Button btnManageImWordSave;

	EditText edtManageImWordCode;
	EditText edtManageImWordCode3r;
	EditText edtManageImWordWord;

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	public static ManageImAddDialog newInstance() {
		ManageImAddDialog btd = new ManageImAddDialog();
						   btd.setCancelable(true);
		return btd;
	}
	
	public void setHandler(ManageImHandler handler){
		this.handler = handler;
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

	public void cancelDialog(){
		this.dismiss();
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {

		getDialog().getWindow().setTitle(getResources().getString(R.string.manage_word_dialog_add));

		activity = getActivity();
		view = inflater.inflate(R.layout.fragment_dialog_add, container, false);

		btnManageImWordCancel = (Button) view.findViewById(R.id.btnManageImWordCancel);
		btnManageImWordCancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				cancelDialog();
			}
		});



		btnManageImWordSave = (Button) view.findViewById(R.id.btnManageImWordSave);
		btnManageImWordSave.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
				alertDialog.setTitle(activity.getResources().getString(R.string.manage_word_dialog_add));
				alertDialog.setMessage(activity.getResources().getString(R.string.manage_word_dialog_add_message));
				alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.dialog_confirm),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								String code = edtManageImWordCode.getText().toString();
								String code3r = edtManageImWordCode3r.getText().toString();
								String text = edtManageImWordWord.getText().toString();
								if(!code.isEmpty() && !text.isEmpty()){
									if(!code3r.isEmpty()){
										code3r = code3r.trim();
									}
									handler.addWord(code, code3r, text);
									dialog.dismiss();
									cancelDialog();
								}else{
									Toast.makeText(activity, R.string.insert_error, Toast.LENGTH_SHORT).show();
								}
							}
						});
				alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getResources().getString(R.string.dialog_cancel),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								dialog.dismiss();
							}
						});
				alertDialog.show();
			}
		});

		edtManageImWordCode = (EditText) view.findViewById(R.id.edtManageImWordCode);
		edtManageImWordCode3r = (EditText) view.findViewById(R.id.edtManageImWordCode3r);
		edtManageImWordWord = (EditText) view.findViewById(R.id.edtManageImWordWord);
		
		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle icicle) {
		super.onSaveInstanceState(icicle);
	}

}
