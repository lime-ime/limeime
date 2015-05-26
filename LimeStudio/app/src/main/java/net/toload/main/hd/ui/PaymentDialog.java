package net.toload.main.hd.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import net.toload.main.hd.Lime;
import net.toload.main.hd.MainActivity;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEPreferenceManager;

public class PaymentDialog extends DialogFragment {

	Activity activity;
	View view;

	boolean paymentready;

	TextView txtPaymentSuccess;

	Button btnPaymentPlan1;
	Button btnPaymentPlan2;
	Button btnPaymentPlan3;
	Button btnPaymentCancel;

	private LIMEPreferenceManager mLIMEPref;

	public static PaymentDialog newInstance() {
		PaymentDialog btd = new PaymentDialog();
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

		getDialog().getWindow().setTitle(getResources().getString(R.string.payment_dialog_title));

		view = inflater.inflate(R.layout.fragment_dialog_payment, container, false);
		this.activity = getActivity();
		this.mLIMEPref = new LIMEPreferenceManager(this.activity);

		btnPaymentPlan1 = (Button) view.findViewById(R.id.btnPaymentPlan1);
		btnPaymentPlan1.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				((MainActivity)getActivity()).purchase("limeime.contribution.plan.2");
				dismiss();
			}
		});
		btnPaymentPlan2 = (Button) view.findViewById(R.id.btnPaymentPlan2);
		btnPaymentPlan2.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				((MainActivity)getActivity()).purchase("limeime.contribution.plan.2");
				dismiss();
			}
		});
		btnPaymentPlan3 = (Button) view.findViewById(R.id.btnPaymentPlan3);
		btnPaymentPlan3.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				((MainActivity)getActivity()).purchase("limeime.contribution.plan.3");
				dismiss();
			}
		});

		btnPaymentCancel = (Button) view.findViewById(R.id.btnPaymentCancel);
		btnPaymentCancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});

		txtPaymentSuccess = (TextView) view.findViewById(R.id.txtPaymentSuccess);
		txtPaymentSuccess.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});

		boolean buycheck = mLIMEPref.getParameterBoolean(Lime.PAYMENT_FLAG);
		if(buycheck){
			txtPaymentSuccess.setVisibility(View.VISIBLE);
			btnPaymentPlan1.setVisibility(View.GONE);
			btnPaymentPlan2.setVisibility(View.GONE);
			btnPaymentPlan3.setVisibility(View.GONE);
			btnPaymentCancel.setVisibility(View.GONE);
		}

		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle icicle) {
		super.onSaveInstanceState(icicle);
	}

}
