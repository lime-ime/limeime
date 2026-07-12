package org.limeime;

import android.app.Activity;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

public class StubActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getBooleanExtra("showEditText", false)) {
            EditText editText = new EditText(this);
            editText.setSingleLine(false);
            editText.setTextSize(24);
            editText.setMinLines(4);
            setContentView(editText);
            editText.requestFocus();
            editText.postDelayed(() -> {
                InputMethodManager imm = getSystemService(InputMethodManager.class);
                if (imm != null) {
                    imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 300);
        }
    }

    /**
     * Create and initialize LIMEService with this activity as context.
     * Used by tests to ensure proper context and lifecycle.
     */
    public LIMEService createLIMEServiceWithContext() {
        LIMEService service = new LIMEService();
        try {
            java.lang.reflect.Method attachBaseContext = android.app.Service.class.getDeclaredMethod("attachBaseContext", android.content.Context.class);
            attachBaseContext.setAccessible(true);
            attachBaseContext.invoke(service, this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to attach base context to LIMEService", e);
        }
        service.onCreate();
        return service;
    }
}
