/*
 *
 *  *
 *  **    Copyright 2026, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 */

package org.limeime;

import static org.junit.Assert.assertEquals;

import android.app.Activity;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.limeime.data.Related;
import org.limeime.ui.controller.ManageImController;
import org.limeime.ui.view.ManageRelatedAdapter;
import org.limeime.ui.view.ManageRelatedFragment;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ManageRelatedDeletionRefreshTest {

    @Test
    public void submittedListIsSnapshotWhenCallerMutatesSource() {
        List<Related> source = new ArrayList<>();
        source.add(related(1, "/084V9P2", "/084V9P2"));
        source.add(related(2, "/084V9P2", "台中"));

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ManageRelatedAdapter adapter = new ManageRelatedAdapter(Mockito.mock(Activity.class));
            adapter.submitList(source);
            assertEquals(2, adapter.getItemCount());

            source.remove(0);

            assertEquals(
                    "Mutating the fragment list must not silently change ListAdapter state",
                    2,
                    adapter.getItemCount());
        });
    }

    @Test
    public void removeRelatedKeepsSubmittedPageImmutableUntilRefresh() throws Exception {
        List<Related> source = new ArrayList<>();
        source.add(related(1, "/084V9P2", "/084V9P2"));
        source.add(related(2, "/084V9P2", "台中"));

        ManageRelatedFragment fragment = new ManageRelatedFragment();
        ManageImController controller = Mockito.mock(ManageImController.class);
        setField(fragment, "relatedlist", source);
        setField(fragment, "manageImController", controller);

        fragment.removeRelated(1);

        assertEquals(
                "The current page must remain unchanged until the controller supplies a new snapshot",
                2,
                source.size());
        Mockito.verify(controller).deleteRelatedPhrase(1);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Related related(int id, String pword, String cword) {
        Related related = new Related();
        related.setId(id);
        related.setPword(pword);
        related.setCword(cword);
        return related;
    }
}
