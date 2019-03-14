package net.toload.main.hd.readmoo;


import android.content.Context;
import android.support.v7.app.AppCompatDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.toload.main.hd.R;


public class IMEInstallationDialog extends AppCompatDialog {
    private final static String TAG = "[IMEInstallationDialog]";


    private TextView m_text;
    private View m_close;
    private Context m_context;

    public IMEInstallationDialog(Context context) {
        super(context, R.style.AppCompatDialog);
        m_context = context;
        init(context);
    }

    private void init(Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View contentView = inflater.inflate(R.layout.dialog_ime, null, false);
        setContentView(contentView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setCancelable(false);
        setCanceledOnTouchOutside(false);
        m_text = (TextView) contentView.findViewById(R.id.text);
        m_close = contentView.findViewById(R.id.close);
        m_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    public void update(String text) {
        m_text.setText(text);
        m_close.setVisibility(View.VISIBLE);
    }
}
