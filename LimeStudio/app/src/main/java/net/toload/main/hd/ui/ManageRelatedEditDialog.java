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
import net.toload.main.hd.data.Related;

public class ManageRelatedEditDialog extends DialogFragment {

	private Activity activity;
	private View view;

	private ManageRelatedHandler handler;

	private Related related;

	private Button btnManageRelatedCancel;
	private Button btnManageRelatedRemove;
	private Button btnManageRelatedUpdate;

	private EditText edtManageRelatedPword;
	private EditText edtManageRelatedCword;
	private EditText edtManageRelatedScore;

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	public static ManageRelatedEditDialog newInstance() {
		ManageRelatedEditDialog btd = new ManageRelatedEditDialog();
						   btd.setCancelable(true);
		return btd;
	}
	
	public void setHandler(ManageRelatedHandler handler, Related related){
		this.related = related;
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

		getDialog().getWindow().setTitle(getResources().getString(R.string.manage_related_dialog_edit));

		activity = getActivity();
		view = inflater.inflate(R.layout.fragment_dialog_related_edit, container, false);

		btnManageRelatedCancel = (Button) view.findViewById(R.id.btnManageRelatedCancel);
		btnManageRelatedCancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				cancelDialog();
			}
		});

		btnManageRelatedRemove = (Button) view.findViewById(R.id.btnManageRelatedRemove);
		btnManageRelatedRemove.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
				alertDialog.setTitle(activity.getResources().getString(R.string.manage_related_dialog_delete));
				alertDialog.setMessage(activity.getResources().getString(R.string.manage_related_dialog_delete_message));
				alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.dialog_confirm),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								handler.removeRelated(related.getId());
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

		btnManageRelatedUpdate = (Button) view.findViewById(R.id.btnManageRelatedUpdate);
		btnManageRelatedUpdate.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
				alertDialog.setTitle(activity.getResources().getString(R.string.manage_related_dialog_edit));
				alertDialog.setMessage(activity.getResources().getString(R.string.manage_related_dialog_message));
				//alertDialog.setIcon(R.drawable.);
				alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.dialog_confirm),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								String pword = edtManageRelatedPword.getText().toString();
								String cword = edtManageRelatedCword.getText().toString();
								String score = edtManageRelatedScore.getText().toString();
								if(!pword.isEmpty() && !cword.isEmpty() ) {
									pword = pword.trim();
									cword = cword.trim();
									int s = 0;
									try{
										s = Integer.parseInt(score);
									}catch(Exception e){}

									handler.updateRelated(related.getId(), pword, cword, s);
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

		edtManageRelatedPword = (EditText) view.findViewById(R.id.edtManageRelatedPword);
		edtManageRelatedCword = (EditText) view.findViewById(R.id.edtManageRelatedCword);
		edtManageRelatedScore = (EditText) view.findViewById(R.id.edtManageRelatedScore);

		edtManageRelatedPword.setText(related.getPword());
		edtManageRelatedPword.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if(edtManageRelatedPword.getText() != null && !edtManageRelatedPword.getText().equals("") &&
						edtManageRelatedPword.getText().length() > 1){
					edtManageRelatedPword.setText(edtManageRelatedPword.getText().subSequence(0,1));
				}
			}
		});

		edtManageRelatedCword.setText(related.getCword());
		edtManageRelatedScore.setText(related.getScore() + "");
		
		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle icicle) {
		super.onSaveInstanceState(icicle);
	}

}
