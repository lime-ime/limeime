package net.toload.main.hd.readmoo;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import net.toload.main.hd.R;


public class MooIMEManager extends BroadcastReceiver {
    private final static String TAG = "[MooIMEManager]";

    private IMEInstallationDialog m_imeDialog;
    private Context m_context;
    private Handler m_handler;


    public MooIMEManager(Context context) {
        m_context = context;
        m_handler = new Handler(context.getMainLooper());

        IntentFilter intentFilter = new IntentFilter("readmoo.ACTION_MOO_INSTALL_IME_STATE");
        context.registerReceiver(this, intentFilter);
    }

    public void release() {
        try {
            m_context.unregisterReceiver(this);
        } catch (Exception e) {

        }
        dismiss();
    }

    public void show() {
        m_handler.post(new Runnable() {
            @Override
            public void run() {
                if (m_imeDialog != null) {
                    Log.e(TAG, "[installLimeIME] IME is installing....");
                    return;
                }

                m_imeDialog = new IMEInstallationDialog(m_context);
                m_imeDialog.getWindow().setGravity(Gravity.CENTER);
                m_imeDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                m_imeDialog.getWindow().setLayout(WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
                m_imeDialog.show();
            }
        });
    }

    private void dismiss() {
        if (m_imeDialog != null) {
            m_imeDialog.dismiss();
            m_imeDialog = null;
        }
    }

    private void done() {
        dismiss();
    }

    private void terminate(final String code) {
        m_handler.post(new Runnable() {
            @Override
            public void run() {
             String text = m_context.getResources().getString(R.string.imeInstallationError) + code;
            m_imeDialog.update(text);
            }
        });
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        int result = intent.getIntExtra("state", State.Error.ordinal());
        String code = intent.getStringExtra("code");
//        Log.d(TAG, "[onReceive] code=" + code + ", state= " + result);


        if (result == State.Done.ordinal())
            done();
        else
            terminate(code);
    }
}
