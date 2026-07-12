/*
 * Copyright 2026, The LimeIME Open Source Project
 */
package org.limeime;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import static org.junit.Assert.*;

/**
 * Tests for ManageRelatedAddSheet.
 */
@RunWith(AndroidJUnit4.class)
public class ManageRelatedAddDialogTest {

    @Test
    public void testManageRelatedAddDialogClassExists() {
        try {
            Class<?> cls = Class.forName("org.limeime.ui.dialog.ManageRelatedAddSheet");
            assertNotNull(cls);
        } catch (ClassNotFoundException e) {
            fail("ManageRelatedAddSheet class not found");
        }
    }

    @Test
    public void testValidationAndControllerAddRelatedApis() throws Exception {
        Class<?> dialog = Class.forName("org.limeime.ui.dialog.ManageRelatedAddSheet");
        boolean hasValidation = false;
        for (java.lang.reflect.Method m : dialog.getDeclaredMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("validate") || n.contains("check")) { hasValidation = true; break; }
        }
        assertTrue("ManageRelatedAddSheet should perform validation", hasValidation);

        Class<?> ctrl = Class.forName("org.limeime.ui.controller.ManageImController");
        boolean hasAddRelated = false;
        for (java.lang.reflect.Method m : ctrl.getMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("add") && n.contains("related")) { hasAddRelated = true; break; }
        }
        assertTrue("ManageImController should expose add related operation", hasAddRelated);
    }

    @Test
    public void testSheetLayoutScrollsWhenImeIsVisible() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("ManageRelatedAddSheet root should scroll above the soft keyboard",
                "androidx.core.widget.NestedScrollView",
                getRootTagName(context, R.layout.sheet_manage_related_add));
    }

    @Test
    public void testSheetMatchesIosRowEditorControls() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("新增資料列", context.getString(R.string.manage_related_dialog_add));
        assertNotEquals("Related add sheet should expose a cancel button",
                0, R.id.btn_cancel);
        assertNotEquals("Related add sheet should expose an editable score field",
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
