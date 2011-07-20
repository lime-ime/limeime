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

package net.toload.main.hd;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import net.toload.main.hd.IDBService;
import net.toload.main.hd.ISearchService;
import net.toload.main.hd.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

/**
 * 
 * @author Art Hung
 * 
 */

public class LIMEMappingLoading extends Activity {

	private LIMEPreferenceManager mLIMEPref = null;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		this.setContentView(R.layout.progress);
		mLIMEPref = new LIMEPreferenceManager(this);
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();

		mLIMEPref.setParameter("db_finish", false);
		
		Thread thread = new Thread() {
			public void run() {
				
				do{
					if(!mLIMEPref.getParameterBoolean("db_finish")){
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}else{
						break;
					}
				}while(true);
				finish();
			}
		};
		thread.start();
	}
		
}