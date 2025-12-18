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

import net.toload.main.hd.data.Word;
import net.toload.main.hd.limedb.LimeDB;

import java.util.List;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class ManageImRunnable implements Runnable{


    private final ManageImHandler handler;
    private final LimeDB datasource;
    private final String table;
    private final String query;
    private final boolean searchRoot;
    private final int maximum;
    private final int offset;

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    public ManageImRunnable(ManageImHandler handler, Activity activity, String table, String query,
                            boolean searchRoot, int maximum, int offset) {
        this.handler = handler;
        this.table = table;
        this.query = query;
        this.searchRoot = searchRoot;
        this.maximum = maximum;
        this.offset = offset;

        datasource = new LimeDB(activity);
    }

    public void run() {
        handler.showProgress();

        handler.updateGridView(loadImWord(table, query, maximum, offset));

        /*if (maximum > 0) {
            handler.updateGridViewInitial(loadImWord(table, getMappingByCode, maximum, offset));
        }else{
            handler.updateGridView(loadImWord(table, getMappingByCode, maximum, offset));
        }*/
    }

    private List<Word> loadImWord(String table, String query, int maximum, int offset){
        List<Word> results;

        results = datasource.loadWord(table, query, this.searchRoot, maximum, offset);
        /*try {
            datasource.open();
            datasource.close();
        } catch (SQLException e) {
            Log.e(TAG, "Error in operation", e);
        }*/
        return results;
    }

}
