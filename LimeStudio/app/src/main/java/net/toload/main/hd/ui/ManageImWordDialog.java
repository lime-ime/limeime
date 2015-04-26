package net.toload.main.hd.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.content.DialogInterface;
import android.app.DialogFragment;

public class DialogQuizExitFragment extends DialogFragment {

	View view;
	Button btnQuizExitConfirm;
	Button btnQuizExitCancel;
	
	//ExitQuizHandler handler;

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	public static DialogQuizExitFragment newInstance() {
		DialogQuizExitFragment btd = new DialogQuizExitFragment();
						   btd.setCancelable(true);
		return btd;
	}
	
	public void setHandler(ExitQuizHandler handler){
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

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {

		getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		view = inflater.inflate(R.layout.dialog_quiz_exit, container, false);
		
		btnQuizExitConfirm = (Button) view.findViewById(R.id.btnQuizExitConfirm);
		btnQuizExitConfirm.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				handler.enterCategoryActivity();
			}});
		
		btnQuizExitCancel = (Button) view.findViewById(R.id.btnQuizExitCancel);
		btnQuizExitCancel.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				dismiss();
			}});
		
		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle icicle) {
		super.onSaveInstanceState(icicle);
	}

}
