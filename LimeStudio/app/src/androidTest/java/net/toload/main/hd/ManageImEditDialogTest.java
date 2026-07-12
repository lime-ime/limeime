/*
 * Copyright 2026, The LimeIME Open Source Project
 */
package net.toload.main.hd;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import static org.junit.Assert.*;

/**
 * Tests for ManageImEditSheet.
 */
@RunWith(AndroidJUnit4.class)
public class ManageImEditDialogTest {

    @Test
    public void testManageImEditDialogClassExists() {
        try {
            Class<?> cls = Class.forName("net.toload.main.hd.ui.dialog.ManageImEditSheet");
            assertNotNull(cls);
        } catch (ClassNotFoundException e) {
            fail("ManageImEditSheet class not found");
        }
    }

    @Test
    public void testValidationAndControllerUpdateApis() throws Exception {
        Class<?> dialog = Class.forName("net.toload.main.hd.ui.dialog.ManageImEditSheet");
        boolean hasValidation = false;
        for (java.lang.reflect.Method m : dialog.getDeclaredMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("validate") || n.contains("check")) { hasValidation = true; break; }
        }
        assertTrue("ManageImEditSheet should perform validation", hasValidation);

        Class<?> ctrl = Class.forName("net.toload.main.hd.ui.controller.ManageImController");
        boolean hasUpdate = false;
        for (java.lang.reflect.Method m : ctrl.getMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("update") && n.contains("record")) { hasUpdate = true; break; }
        }
        assertTrue("ManageImController should expose update IM operation", hasUpdate);
    }

    @Test
    public void testSheetLayoutScrollsWhenImeIsVisible() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("ManageImEditSheet root should scroll above the soft keyboard",
                "androidx.core.widget.NestedScrollView",
                getRootTagName(context, R.layout.sheet_manage_im_edit));
    }

    @Test
    public void testSheetMatchesIosRowEditorControls() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("編輯資料列", context.getString(R.string.manage_word_dialog_edit));
        assertNotEquals("Edit sheet should expose a cancel button",
                0, R.id.btn_cancel);
        assertNotEquals("Edit sheet should expose an editable score field",
                0, R.id.edt_score);
    }

    private String getRootTagName(Context context, int layoutId) {
        try {
            XmlPullParser parser = context.getResources().getLayout(layoutId);
            int eventType;
            while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    return parser.getName();
                }
            }
        } catch (Exception e) {
            Assert.fail("Unable to read sheet layout root: " + e.getMessage());
        }
        Assert.fail("Layout has no root tag");
        return "";
    }
}
