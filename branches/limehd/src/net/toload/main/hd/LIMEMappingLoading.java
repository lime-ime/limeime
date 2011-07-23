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

import net.toload.main.hd.R;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.widget.TextView;

/**
 * 
 * @author Art Hung
 * 
 */

public class LIMEMappingLoading extends Activity {

	private LIMEPreferenceManager mLIMEPref = null;
	TextView txtLoadingStatus = null;
    final Handler mHandler = new Handler();
    // Create runnable for posting
    final Runnable mUpdateUI = new Runnable() {
        public void run() {
        	txtLoadingStatus.setText( mLIMEPref.getParameterInt("im_loading_table_percent") + "%");
        }
    };

	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		this.setContentView(R.layout.progress);
		mLIMEPref = new LIMEPreferenceManager(this);
		txtLoadingStatus = (TextView) findViewById(R.id.txtLoadingStatus);
		
	}

	@Override
	protected void onResume() {

		super.onResume();
		
		mLIMEPref.setParameter("db_finish", false);
		
		Thread thread = new Thread() {
			public void run() {
				
				do{
					if(!mLIMEPref.getParameterBoolean("db_finish")){
						mHandler.post(mUpdateUI);

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

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK)) {
	        return true;
	    }

		return super.onKeyDown(keyCode, event);
	}

	
		
}