/*    
**    Copyright 2010, The LimeIME Open Source Project
** 
**    Project Url: http://code.google.com/p/limeime/
**                 http://android.toload.net/
**
**    This program is free software: you can redistribute it and/or modify
**    it under the terms of the GNU General Public License as published by
**    the Free Software Foundation, either version 3 of the License, or
**    (at your option) any later version.

**    This program is distributed in the hope that it will be useful,
**    but WITHOUT ANY WARRANTY; without even the implied warranty of
**    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
**    GNU General Public License for more details.

**    You should have received a copy of the GNU General Public License
**    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package net.toload.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;

/**
 * @author Art Hung
 */
public class LIMEMenu extends TabActivity {
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        final TabHost tabHost = getTabHost();

        int tabno = 0;

		File checkSdFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);
		File checkDbFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
		if(!checkSdFile.exists() && !checkDbFile.exists()){
			tabno = 2;
		}
		
        tabHost.addTab(tabHost.newTabSpec("tab1")
        		.setIndicator(this.getText(R.string.l3_tab_manage))
                .setContent(new Intent(this, LIMEIMSetting.class)));
        
        tabHost.addTab(tabHost.newTabSpec("tab2")
        		.setIndicator(this.getText(R.string.l3_tab_preference))
        		.setContent(new Intent(this, LIMEPreference.class)));

        tabHost.addTab(tabHost.newTabSpec("tab3")
        		.setIndicator(this.getText(R.string.l3_tab_initial))
                .setContent(new Intent(this, LIMEInitial.class)));
        /*
        tabHost.addTab(tabHost.newTabSpec("tab4")
        		.setIndicator(this.getText(R.string.l3_tab_bluetooth))
                .setContent(new Intent(this, LIMEBluetooth.class)));*/

        if(tabno != 0){
            tabHost.setCurrentTab(tabno);
        }
        
       /* WindowManager manager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        
        Log.i("ART",  display.getWidth() + " * "+ display.getHeight());*/
        // 09-16 15:53:47.042: INFO/ART(257): 320 * 480 OK

        
    }
	
}
