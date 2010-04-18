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

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

/**
 * @author Art Hung
 */
public class LIMEPreference extends PreferenceActivity
{
	private Context ctx = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //--------------------
        //Jeremy '10, 4, 18 .  
        if (ctx == null) {
			ctx = this.getApplicationContext();
		}
        int tempRecAmount = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ctx).getString("similiar_list", "150"));	
        if(tempRecAmount < 30){
        	PreferenceManager.getDefaultSharedPreferences(ctx).edit().putString("similiar_list", "150").commit();
        }
        //-----------------------
        addPreferencesFromResource(R.xml.preference);
    }
}