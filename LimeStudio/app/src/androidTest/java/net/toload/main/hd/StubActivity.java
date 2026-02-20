package net.toload.main.hd;

import android.app.Activity;
import android.os.Bundle;

public class StubActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Test-only stub; no UI required
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
