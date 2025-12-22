/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd.ui;

import android.app.Activity;

import net.toload.main.hd.SearchServer;
import net.toload.main.hd.data.Related;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class ManageRelatedRunnable implements Runnable{


    private final ManageRelatedHandler handler;
    private final SearchServer searchServer;
    private final String query;
    private final int maximum;
    private final int offset;

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    public ManageRelatedRunnable(ManageRelatedHandler handler, Activity activity, String query, int maximum, int offset) {
        this.handler = handler;
        this.query = query;
        this.maximum = maximum;
        this.offset = offset;

        searchServer = new SearchServer(activity);
    }

    public void run() {
        handler.showProgress();

        handler.updateGridView(getRelated(query, maximum, offset));

        /*if(maximum > 0){
            handler.updateGridViewInitial(loadRelated(getMappingByCode, maximum));
        }else{
            handler.updateGridView(loadRelated(getMappingByCode, maximum));
        }*/
    }

    private List<Related> getRelated(String pword, int maximum, int offset){
        List<Related> results;

        results = searchServer.getRelatedByWord(pword, maximum, offset);

        return results;
    }

}
