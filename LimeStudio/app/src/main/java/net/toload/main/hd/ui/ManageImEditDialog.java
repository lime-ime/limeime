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
import android.widget.TextView;
import android.widget.Toast;

import net.toload.main.hd.Lime;
import net.toload.main.hd.R;
import net.toload.main.hd.data.Word;

public class ManageImEditDialog extends DialogFragment {

	private Activity activity;
	private View view;

	private String imtype;

	//Button btnQuizExitConfirm;
	//Button btnQuizExitCancel;

	private ManageImHandler handler;

	private Word word;

	private Button btnManageImWordCancel;
	private Button btnManageImWordRemove;
	private Button btnManageImWordUpdate;

	private EditText edtManageImWordCode;
	private EditText edtManageImWordCode3r;
	private EditText edtManageImWordWord;

	private TextView txtManageImWordCode3r;

	public ManageImEditDialog() {}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	public static ManageImEditDialog newInstance(String imtype) {
		ManageImEditDialog btd = new ManageImEditDialog();
						   btd.setCancelable(true);
		Bundle bundle = new Bundle(1);
		bundle.putString("imtype", imtype);
		btd.setArguments(bundle);
		return btd;
	}
	
	public void setHandler(ManageImHandler handler, Word word){
		this.word = word;
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

		getDialog().getWindow().setTitle(getResources().getString(R.string.manage_word_dialog_edit));

		imtype = getArguments().getString("imtype");

		activity = getActivity();
		view = inflater.inflate(R.layout.fragment_dialog_edit, container, false);

		btnManageImWordCancel = (Button) view.findViewById(R.id.btnManageImWordCancel);
		btnManageImWordCancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				cancelDialog();
			}
		});

		btnManageImWordRemove = (Button) view.findViewById(R.id.btnManageImWordRemove);
		btnManageImWordRemove.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
				alertDialog.setTitle(activity.getResources().getString(R.string.manage_word_dialog_delete));
				alertDialog.setMessage(activity.getResources().getString(R.string.manage_word_dialog_delete_message));
				//alertDialog.setIcon(R.drawable.);
				alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.dialog_confirm),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								handler.removeWord(word.getId());
								dialog.dismiss();
								cancelDialog();
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

		btnManageImWordUpdate = (Button) view.findViewById(R.id.btnManageImWordUpdate);
		btnManageImWordUpdate.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
				alertDialog.setTitle(activity.getResources().getString(R.string.manage_word_dialog_edit));
				alertDialog.setMessage(activity.getResources().getString(R.string.manage_word_dialog_message));
				//alertDialog.setIcon(R.drawable.);
				alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.dialog_confirm),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								String code = edtManageImWordCode.getText().toString();
								String code3r = edtManageImWordCode3r.getText().toString();
								String text = edtManageImWordWord.getText().toString();
								if(!code.isEmpty() && !text.isEmpty() && (
										!word.getCode().equalsIgnoreCase(code) ||
												(word.getCode3r() != null && !word.getCode().equalsIgnoreCase(code3r)) ||
										!word.getWord().equalsIgnoreCase(text) ) ) {
									if(!code3r.isEmpty()){
										code3r = code3r.trim();
									}
									handler.updateWord(word.getId(), code, code3r, text);
									dialog.dismiss();
									cancelDialog();
								}else{
									Toast.makeText(activity, R.string.update_error, Toast.LENGTH_SHORT).show();
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

		edtManageImWordCode.setText(word.getCode());
		edtManageImWordCode3r.setText(word.getCode3r());
		edtManageImWordWord.setText(word.getWord());

		txtManageImWordCode3r = (TextView) view.findViewById(R.id.txtManageImWordCode3r);

		if(!imtype.equals(Lime.DB_TABLE_DAYI)){
			edtManageImWordCode3r.setVisibility(View.GONE);
			txtManageImWordCode3r.setVisibility(View.GONE);
		}
		
		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle icicle) {
		super.onSaveInstanceState(icicle);
	}

}
