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

package net.toload.main.hd.limesettings;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


import net.toload.main.hd.R;
import net.toload.main.hd.global.KeyboardObj;
import net.toload.main.hd.global.LIME;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

/**
 * 
 * @author Art Hung
 * 
 */

public class LIMEMappingSetting extends Activity {
	

		//private AlertDialog ad;
		//private ArrayList<File> filelist;
		//private boolean hasSelectFile;
		
		private DBServer DBSrv = null;
		//private SearchServer SearchSrv = null;

		Button btnBackToPreviousPage = null;
		Button btnLoadMapping = null;
		Button btnResetMapping = null;
		Button btnSelectKeyboard = null;
		
		TextView labSource = null;
		TextView labVersion = null;
		TextView labTotalAmount = null;
		TextView labImportDate = null;
		TextView labMappingSettingTitle = null;
		TextView labKeyboard = null;
		private LinearLayout kbLinearLayout;
		
		LinearLayout extendLayout = null;
		LinearLayout extendLayout2 = null;
		LinearLayout extendLayout3 = null;
		Button extendButton = null;
		
		private String imtype = null;
		List<KeyboardObj> kblist = null;
		
		//LIMEPreferenceManager mLIMEPref;
		ConnectivityManager connManager = null;
		
		private AlertDialog mOptionsDialog;
		ListView listview;
		List<File> flist;
		LinearLayout toplayout;
		
		Context ctx;
		
		
		/** Called when the activity is first created. */
		@Override
		public void onCreate(Bundle icicle) {
			
			super.onCreate(icicle);
			this.setContentView(R.layout.kbsetting);

			ctx = this;
			flist = new ArrayList<File>();

			// Startup Service
			//getApplicationContext().bindService(new Intent(IDBService.class.getName()), serConn, Context.BIND_AUTO_CREATE);
			DBSrv = new DBServer(getApplicationContext());
			//getApplicationContext().bindService(new Intent(ISearchService.class.getName()), serConn2, Context.BIND_AUTO_CREATE);
			
	        connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE); 

			//mLIMEPref = new LIMEPreferenceManager(this.getApplicationContext());
			
	        try{
		        Bundle bundle = this.getIntent().getExtras();
		        if(bundle != null){
		        	imtype = bundle.getString("keyboard");
		        }
	        }catch(Exception e){
	        	e.printStackTrace();
	        }
	        
	        if(DBSrv != null && DBSrv.isRemoteFileDownloading()){
				DBSrv.abortRemoteFileDownload();
				
			}
	        
			// Initial Buttons
			initialButton();
			
			// Setup Extended Button

			extendLayout = (LinearLayout) findViewById(R.id.extendLayout);
			extendLayout2 = (LinearLayout) findViewById(R.id.extendLayout2);
			extendLayout3 = (LinearLayout) findViewById(R.id.extendLayout3);
			
			if(imtype != null && imtype.equals("dayi")){
				Button extendButton = new Button(this);
				extendButton.setText(getResources().getString(R.string.l3_im_download_from_dayi));
				extendLayout.addView(extendButton);

				extendButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_message_table_download_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {
	     						if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){					        
	    							try {
	    								//hasSelectFile = true;
	    								resetLabelInfo();
	    	    						//DBSrv.resetMapping("dayi");
	    								DBSrv.downloadDayi();
	    	    						startLoadingWindow();

	    								//mLIMEPref.setParameter("im_loading", true);
	    								//mLIMEPref.setParameter("im_loading_table", imtype);

	    								
	    							} catch (RemoteException e) {
	    								//mLIMEPref.setParameter("im_loading", false);
	    								e.printStackTrace();
	    							}
	    				        }else{
	    				        	Toast.makeText( ctx, getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
	    				    		//mLIMEPref.setParameter("db_finish", true);
	    						}
			    	        }
			    	     });
	        
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     });   
	        
						AlertDialog alert = builder.create();
									alert.show();
						
					}
				});
				
			}else if(imtype != null && imtype.equals("phonetic")){
				Button extendButton = new Button(this);
				extendButton.setText(getResources().getString(R.string.l3_im_download_from_phonetic));
				extendLayout.addView(extendButton);

				extendButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_message_table_download_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {
	    						
	    						if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){					        
	    							try {
	    								//hasSelectFile = true;
	    								resetLabelInfo();
	    								DBSrv.downloadPhonetic();
	    								startLoadingWindow();
	    							} catch (RemoteException e) {
	    								e.printStackTrace();
	    							}
	    				        }else{
	    				        	Toast.makeText(ctx, getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
	    				    		//mLIMEPref.setParameter("db_finish", true);
	    						}
			    	        }
			    	     });
	        
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     });   
	        
						AlertDialog alert = builder.create();
									alert.show();
						

					}
				});
				
				Button extendButton2 = new Button(this);
				extendButton2.setText(getResources().getString(R.string.l3_im_download_from_phonetic_adv));
				extendLayout2.addView(extendButton2);

				extendButton2.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_message_table_download_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {

								
	    			
	    						
	    						if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){					        
	    							try {
	    								Toast.makeText(ctx, getText(R.string.l3_im_download_from_phonetic_adv_warning), Toast.LENGTH_SHORT).show();
		    				    		
	    								//hasSelectFile = true;
	    								resetLabelInfo();
	    								DBSrv.downloadPhoneticAdv();
	    								startLoadingWindow();
	    							} catch (RemoteException e) {
	    								e.printStackTrace();
	    							}
	    				        }else{
	    				        	Toast.makeText(ctx, getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
	    				    	}
			    	        }
			    	     });
	        
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     });   
	        
						AlertDialog alert = builder.create();
									alert.show();
						

					}
				});
				

				
				Button extendButton3 = new Button(this);
				extendButton3.setText(getResources().getString(R.string.l3_im_download_from_phonetic_cns));
				extendLayout3.addView(extendButton3);

				extendButton3.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_message_table_download_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {
	    						
	    						if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){					        
	    							try {
	    								Toast.makeText(ctx, getText(R.string.l3_im_download_from_phonetic_cns_warning), Toast.LENGTH_SHORT).show();
		    				    		
	    								//hasSelectFile = true;
	    								resetLabelInfo();
	    								DBSrv.downloadPhoneticCns();
	    								startLoadingWindow();
	    							} catch (RemoteException e) {
	    								e.printStackTrace();
	    							}
	    				        }else{
	    				        	Toast.makeText(ctx, getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
	    				    	}
			    	        }
			    	     });
	        
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     });   
	        
						AlertDialog alert = builder.create();
									alert.show();
						

					}
				});
				
			}else if(imtype != null && imtype.equals("cj")){
				Button extendButton = new Button(this);
				extendButton.setText(getResources().getString(R.string.l3_im_download_from_cj));
				extendLayout.addView(extendButton);

				extendButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_message_table_download_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {

	    						
	    						
	    						if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){					        
	    							try {
	    								//hasSelectFile = true;
	    								resetLabelInfo();
	    	    						//DBSrv.resetMapping("cj");
	    								DBSrv.downloadCj();
	    								startLoadingWindow();
	    								//DBSrv.setImInfo("cj", "keyboard",  "嚙踝赯�閰剁��改蕭嚙�; // set this in LIMEDb loadfile()
	    								
	    								//mLIMEPref.setParameter("im_loading", true);
	    								//mLIMEPref.setParameter("im_loading_table", imtype);
	    							} catch (RemoteException e) {
	    								//mLIMEPref.setParameter("im_loading", false);
	    								e.printStackTrace();
	    							}
	    				        }else{
	    				        	Toast.makeText(ctx, getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
	    				    		//mLIMEPref.setParameter("db_finish", true);
	    						}
			    	        }
			    	     });
	        
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     });   
	        
						AlertDialog alert = builder.create();
									alert.show();
						

					}
				});

				Button extendButton2 = new Button(this);
				extendButton2.setText(getResources().getString(R.string.l3_im_download_from_cj_cns));
				extendLayout2.addView(extendButton2);

				extendButton2.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_message_table_download_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {

	    						if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){					        
	    							try {
	    								Toast.makeText(ctx, getText(R.string.l3_im_download_from_phonetic_cns_warning), Toast.LENGTH_SHORT).show();
		    				    		
	    								//hasSelectFile = true;
	    								resetLabelInfo();
	    								DBSrv.downloadCjCns();
	    								startLoadingWindow();
	    							} catch (RemoteException e) {
	    								e.printStackTrace();
	    							}
	    				        }else{
	    				        	Toast.makeText(ctx, getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
	    				    	}
			    	        }
			    	     });
	        
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     });   
	        
						AlertDialog alert = builder.create();
									alert.show();
						
					}
				});
				
			}else if(imtype != null && imtype.equals("scj")){
				Button extendButton = new Button(this);
				extendButton.setText(getResources().getString(R.string.l3_im_download_from_scj));
				extendLayout.addView(extendButton);

				extendButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_message_table_download_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {

	    					
	    						
	    						if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){					        
	    							try {
	    								//hasSelectFile = true;
	    								resetLabelInfo();
	    	    						//DBSrv.resetMapping("scj");
	    								DBSrv.downloadScj();
	    								startLoadingWindow();
	    								//DBSrv.setImInfo("scj", "keyboard",  "嚙踝赯�閰剁��改蕭嚙�; // set this in LIMEDb loadfile()
	    								
	    								//mLIMEPref.setParameter("im_loading", true);
	    								//mLIMEPref.setParameter("im_loading_table", imtype);
	    							} catch (RemoteException e) {
	    								//mLIMEPref.setParameter("im_loading", false);
	    								e.printStackTrace();
	    							}
	    				        }else{
	    				        	Toast.makeText(ctx, getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
	    				    		//mLIMEPref.setParameter("db_finish", true);
	    						}
			    	        }
			    	     });
	        
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     });   
	        
						AlertDialog alert = builder.create();
									alert.show();
						

					}
				});
				
			}else if(imtype != null && imtype.equals("cj5")){
				Button extendButton = new Button(this);
				extendButton.setText(getResources().getString(R.string.l3_im_download_from_cj5));
				extendLayout.addView(extendButton);

				extendButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_message_table_download_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {

	    						
	    						
	    						if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){					        
	    							try {
	    								//hasSelectFile = true;
	    								resetLabelInfo();
	    	    						//DBSrv.resetMapping("cj5");
	    								DBSrv.downloadCj5();
	    								startLoadingWindow();
	    								//DBSrv.setImInfo("cj5", "keyboard",  "嚙踝赯�閰剁��改蕭嚙�; // set this in LIMEDb loadfile()
	    								
	    								//mLIMEPref.setParameter("im_loading", true);
	    								//mLIMEPref.setParameter("im_loading_table", imtype);
	    							} catch (RemoteException e) {
	    								//mLIMEPref.setParameter("im_loading", false);
	    								e.printStackTrace();
	    							}
	    				        }else{
	    				        	Toast.makeText(ctx, getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
	    				    		//mLIMEPref.setParameter("db_finish", true);
	    						}
			    	        }
			    	     });
	        
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     });   
	        
						AlertDialog alert = builder.create();
									alert.show();
						

					}
				});
				
			}else if(imtype != null && imtype.equals("ecj")){
				Button extendButton = new Button(this);
				extendButton.setText(getResources().getString(R.string.l3_im_download_from_ecj));
				extendLayout.addView(extendButton);

				extendButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_message_table_download_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {

								
	    						
	    						
	    						if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){					        
	    							try {
	    								//hasSelectFile = true;
	    								resetLabelInfo();
	    	    						//DBSrv.resetMapping("ecj");
	    								DBSrv.downloadEcj();
	    								startLoadingWindow();
	    								//DBSrv.setImInfo("ecj", "keyboard",  "嚙踝赯�閰剁��改蕭嚙�; // set this in LIMEDb loadfile()
	    								
	    								//mLIMEPref.setParameter("im_loading", true);
	    								//mLIMEPref.setParameter("im_loading_table", imtype);
	    							} catch (RemoteException e) {
	    								//mLIMEPref.setParameter("im_loading", false);
	    								e.printStackTrace();
	    							}
	    				        }else{
	    				        	Toast.makeText(ctx, getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
	    				    		//mLIMEPref.setParameter("db_finish", true);
	    						}
			    	        }
			    	     });
	        
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     });   
	        
						AlertDialog alert = builder.create();
									alert.show();
						

					}
				});
				
			}else if(imtype != null && imtype.equals("ez")){
				Button extendButton = new Button(this);
				extendButton.setText(getResources().getString(R.string.l3_im_download_from_ez));
				extendLayout.addView(extendButton);

				extendButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_message_table_download_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {

								
	    						startLoadingWindow();
	    						
	    						if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){					        
	    							try {
	    								//hasSelectFile = true;
	    								resetLabelInfo();
	    	    						//DBSrv.resetMapping("ez");
	    								DBSrv.downloadEz();
	    								startLoadingWindow();
	    								//DBSrv.setImInfo("ez", "keyboard",  "�嚙賡�閰剁��改蕭嚙�; // set this in LIMEDb loadfile()
	    								
	    								//mLIMEPref.setParameter("im_loading", true);
	    								//mLIMEPref.setParameter("im_loading_table", imtype);
	    							} catch (RemoteException e) {
	    								//mLIMEPref.setParameter("im_loading", false);
	    								e.printStackTrace();
	    							}
	    				        }else{
	    				        	Toast.makeText(ctx, getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
	    				    		//mLIMEPref.setParameter("db_finish", true);
	    						}
			    	        }
			    	     });
	        
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     });   
	        
						AlertDialog alert = builder.create();
									alert.show();
						

					}
				});
				
			}else if(imtype != null && imtype.equals("array")){
				Button extendButton = new Button(this);
				extendButton.setText(getResources().getString(R.string.l3_im_download_from_array));
				extendLayout.addView(extendButton);

				extendButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_message_table_download_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {
	     						
	    						
	    						
	    						if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){					        
	    						try {
	    								//hasSelectFile = true;
	    								resetLabelInfo();
	    	    						//DBSrv.resetMapping("array");
	    								DBSrv.downloadArray();
	    								startLoadingWindow();
	    								//DBSrv.setImInfo("array", "keyboard", "�蛛嚙賡�閰剁��改蕭嚙�; // set this in LIMEDb loadfile()
	    								
	    								//mLIMEPref.setParameter("im_loading", true);
	    								//mLIMEPref.setParameter("im_loading_table", imtype);
	    							} catch (RemoteException e) {
	    								//mLIMEPref.setParameter("im_loading", false);
	    								e.printStackTrace();
	    							}
	    				        }else{
	    				        	Toast.makeText(ctx, getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
	    				    		//mLIMEPref.setParameter("db_finish", true);
	    						}
			    	        }
			    	     });
	        
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     });   
	        
						AlertDialog alert = builder.create();
									alert.show();
						
							

					}
				});
				
			}else if(imtype != null && imtype.equals("array10")){
				Button extendButton = new Button(this);
				extendButton.setText(getResources().getString(R.string.l3_im_download_from_array10));
				extendLayout.addView(extendButton);

				extendButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_message_table_download_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {
	     						
	    						
	    						if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){					        
	    							try {
	    								//hasSelectFile = true;
	    								resetLabelInfo();
	    	    						//DBSrv.resetMapping("array10");
	    								DBSrv.downloadArray10();

	    	    						startLoadingWindow();
	    								//DBSrv.setImInfo("array10", "keyboard",  "嚙賡�摨蕭閰剁蕭嚙質�謆�); // set this in LIMEDb loadfile()
	    								
	    								//mLIMEPref.setParameter("im_loading", true);
	    								//mLIMEPref.setParameter("im_loading_table", imtype);
	    							} catch (RemoteException e) {
	    								//mLIMEPref.setParameter("im_loading", false);
	    								e.printStackTrace();
	    							}
	    				        }else{
	    				        	Toast.makeText(ctx, getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
	    				    		//mLIMEPref.setParameter("db_finish", true);
	    						}
			    	        }
			    	     });
	        
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     });   
	        
						AlertDialog alert = builder.create();
									alert.show();
						
									
							

					}
				});
				
			}else if(imtype != null && imtype.equals("wb")){
				Button extendButton = new Button(this);
				extendButton.setText(getResources().getString(R.string.l3_im_download_from_wb));
				extendLayout.addView(extendButton);

				extendButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_message_table_download_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {
	     						
	    						
	    						if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){					        
	    							try {
	    								//hasSelectFile = true;
	    								resetLabelInfo();
	    	    						//DBSrv.resetMapping("wb");
	    								DBSrv.downloadWb();
	    								startLoadingWindow();
	    								//DBSrv.setImInfo("wb", "keyboard", "�叟�嚙賡�閰剁��改蕭嚙�; // set this in LIMEDb loadfile()
	    								
	    								//mLIMEPref.setParameter("im_loading", true);
	    								//mLIMEPref.setParameter("im_loading_table", imtype);
	    							} catch (RemoteException e) {
	    								//mLIMEPref.setParameter("im_loading", false);
	    								e.printStackTrace();
	    							}
	    				        }else{
	    				        	Toast.makeText(ctx, getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
	    				    		//mLIMEPref.setParameter("db_finish", true);
	    						}
			    	        }
			    	     });
	        
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     });   
	        
						AlertDialog alert = builder.create();
									alert.show();
						
					}
				});

				// Remove useless layout LinearLayout05
				TextView t1 = (TextView) findViewById(R.id.txtKeyboardInfo);
						 t1.setVisibility(View.INVISIBLE);
				TextView t2 = (TextView) findViewById(R.id.txtSelectKeyboard);
				 		 t2.setVisibility(View.INVISIBLE);
				TextView t3 = (TextView) findViewById(R.id.labKeyboard);
				 		 t3.setVisibility(View.INVISIBLE);
				Button b1 = (Button) findViewById(R.id.btnSelectKeyboard);
				 	   b1.setVisibility(View.INVISIBLE);
							 
			}else if(imtype != null && imtype.equals("hs")){
				
				
				Button extendButton = new Button(this);
				extendButton.setText(getResources().getString(R.string.l3_im_download_from_hs));
				extendLayout.addView(extendButton);

				extendButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
	     				builder.setMessage(getText(R.string.l3_message_table_download_confirm));
	     				builder.setCancelable(false);
	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	     					public void onClick(DialogInterface dialog, int id) {
	     						
	    						
	    						if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){					        
	    							try {
	    								//hasSelectFile = true;
	    								resetLabelInfo();
	    								DBSrv.downloadHs();
	    								startLoadingWindow();
	    							} catch (RemoteException e) {
	    								e.printStackTrace();
	    							}
	    				        }else{
	    				        	Toast.makeText(ctx, getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
	    				    	}
			    	        }
			    	     });
	        
			    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			    	    	public void onClick(DialogInterface dialog, int id) {
			    	        	}
			    	     });   
	        
						AlertDialog alert = builder.create();
									alert.show();
					}
				});

				// Remove useless layout LinearLayout05
				TextView t1 = (TextView) findViewById(R.id.txtKeyboardInfo);
						 t1.setVisibility(View.INVISIBLE);
				TextView t2 = (TextView) findViewById(R.id.txtSelectKeyboard);
				 		 t2.setVisibility(View.INVISIBLE);
				TextView t3 = (TextView) findViewById(R.id.labKeyboard);
				 		 t3.setVisibility(View.INVISIBLE);
				Button b1 = (Button) findViewById(R.id.btnSelectKeyboard);
				 	   b1.setVisibility(View.INVISIBLE);
							 
			}else{
				extendLayout.removeView(extendButton);
			}
			
			btnSelectKeyboard.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					showKeyboardPicker();
				}
			});
			
			btnBackToPreviousPage.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					finish();
				}
			});
			
			btnLoadMapping.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					//hasSelectFile = false;
                	selectLimeFile(LIME.IM_LOAD_LIME_ROOT_DIRECTORY, imtype);
				}
			});

			kbLinearLayout.setOnTouchListener(new OnTouchListener(){
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					updateLabelInfo();
					return false;
				}
			});
			
			btnResetMapping.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					
					AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
     				builder.setMessage(getText(R.string.l3_message_table_reset_confirm));
     				builder.setCancelable(false);
     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
     					public void onClick(DialogInterface dialog, int id) {
		    					initialButton();
		    					try {
		    						resetLabelInfo();
		    						DBSrv.resetMapping(imtype);
		    						//mLIMEPref.setParameter("im_loading", false);
		    						//mLIMEPref.setParameter("im_loading_table", "");
		    						btnLoadMapping.setEnabled(true);
		    					} catch (RemoteException e) {
		    						e.printStackTrace();
		    					}
		    	        	}
		    	     });
        
		    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
		    	    	public void onClick(DialogInterface dialog, int id) {
		    	        	}
		    	     });   
        
					AlertDialog alert = builder.create();
								alert.show();
								
					
				}
			});
			

		}
		
		/* (non-Javadoc)
		 * @see android.app.Activity#onStart()
		 */
		@Override
		protected void onStart() {
			super.onStart();
			initialButton();
		}
		

		/* (non-Javadoc)
		 * @see android.app.Activity#onStart()
		 */
		@Override
		protected void onResume() {
			super.onResume();
			
			if(DBSrv!= null){
				if(	DBSrv.isRemoteFileDownloading() ||
						DBSrv.isLoadingMappingThreadAlive()){
					startLoadingWindow();
				//} else 	if(! DBSrv.isLoadingMappingFinished() ){ 
				//		resetLabelInfo();
				}
				initialButton();
				updateLabelInfo();
			}
			
			
		}
		
		
		private void initialButton(){

			btnBackToPreviousPage = (Button) findViewById(R.id.btnBackToPreviousPage);
			btnLoadMapping =  (Button) findViewById(R.id.btnLoadMapping);
			btnResetMapping =  (Button) findViewById(R.id.btnResetMapping);
			btnSelectKeyboard = (Button) findViewById(R.id.btnSelectKeyboard);

			labSource = (TextView) findViewById(R.id.labSource);	
			labVersion = (TextView) findViewById(R.id.labVersion);	
			labTotalAmount = (TextView) findViewById(R.id.labTotalAmount);	
			labImportDate = (TextView) findViewById(R.id.labImportDate);
			labMappingSettingTitle = (TextView) findViewById(R.id.labMappingSettingTitle);
			labKeyboard = (TextView) findViewById(R.id.labKeyboard);
			
			kbLinearLayout = (LinearLayout) this.findViewById(R.id.kbLinearLayout);
			
			if(DBSrv!=null && (DBSrv.isRemoteFileDownloading() ||
				DBSrv.isLoadingMappingThreadAlive()) ){
				btnLoadMapping.setEnabled(false);
			}else
				btnLoadMapping.setEnabled(true);
			
		}

		public void resetLabelInfo(){
			labSource.setText("");
			labVersion.setText("");
			labTotalAmount.setText("");
			labImportDate.setText("");
			labKeyboard.setText("");
		}

		public void updateLabelInfo(){
			
			if(DBSrv!=null && DBSrv.isLoadingMappingThreadAlive()){
				labSource.setText("Loading...");
				labVersion.setText("Loading...");
				labTotalAmount.setText("Loading...");
				labImportDate.setText("Loading...");
			}else{
				try{
					labSource.setText(DBSrv.getImInfo(imtype, "source"));
					labVersion.setText(DBSrv.getImInfo(imtype, "name"));
					labTotalAmount.setText(DBSrv.getImInfo(imtype, "amount"));
					labImportDate.setText(DBSrv.getImInfo(imtype, "import"));
				}catch(Exception e){
					e.printStackTrace();
				}
			}
			
			
			if(imtype.equalsIgnoreCase("cj")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_cj) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("scj")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_scj) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("ez")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_eazy) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("array")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_array) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("array10")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_array10) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("dayi")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_dayi) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("custom")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_default) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("phonetic")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_phonetic) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("cj5")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_cj5) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("ecj")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_ecj) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("wb")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_wb) +" "+ getText(R.string.l3_im_setting_title) );
			}
			
			// Display Keyboard Selection
			try {
				labKeyboard.setText(DBSrv.getImInfo(imtype, "keyboard"));
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			
		}

//		private ServiceConnection serConn = new ServiceConnection() {
//			public void onServiceConnected(ComponentName name, IBinder service) {
//				//Log.i("ART","DB Service connected!");
//				if(DBSrv == null){
//					DBSrv = IDBService.Stub.asInterface(service);
//					updateLabelInfo();
//				}
//			}
//			public void onServiceDisconnected(ComponentName name) {
//				//Log.i("ART","DB Service disconnected!");
//			}
//		};

		/*
		 * Construct SerConn
		 *
		private ServiceConnection serConn2 = new ServiceConnection() {
			public void onServiceConnected(ComponentName name, IBinder service) {
				SearchSrv = ISearchService.Stub.asInterface(service);
				try {
					SearchSrv.clear();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}

			public void onServiceDisconnected(ComponentName name) {
			}
		};
		
		/**
		 * Select file to be import from the list
		 * 
		 * @param path
		 */
		public void selectLimeFile(String srcpath, String tablename) {

			   final Dialog dialog = new Dialog(this);
			   		  dialog.setContentView(R.layout.target);
			          dialog.setTitle(this.getResources().getText(R.string.lime_setting_btn_load_local_notice));
   	                  dialog.setCancelable(true);
   	           Button button = (Button) dialog.findViewById(R.id.btn_loading_sync_cancel);
		   	          button.setOnClickListener(new OnClickListener() {
		                @Override
		                    public void onClick(View v) {
		                        dialog.dismiss();
		                    }
		                });
		   	          
		   	   listview = (ListView) dialog.findViewById(R.id.listview_loading_target);
		   	   toplayout = (LinearLayout) dialog.findViewById(R.id.linearlayout_loading_confirm_top);
		   	   listview.setAdapter(getAdapter(new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY)));
		   	   
		   	   createNavigationButtons(new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY));
		   	   listview.setOnItemClickListener(new OnItemClickListener(){

				@Override
				public void onItemClick(AdapterView<?> arg0, View arg1,
						int position, long arg3) {
					File f = flist.get(position);
					if(f.isDirectory()){
						listview.setAdapter(getAdapter(f));
						createNavigationButtons(f);
					}else{
						getAvailableFiles(f.getAbsolutePath());
                        dialog.dismiss();
					}
				}
		   		   
		   	   });
			   dialog.show();

			
			
			/*
			// Retrieve Filelist
			filelist = getAvailableFiles(srcpath, tablename);

			ArrayList<String> showlist = new ArrayList<String>();

			int count = 0;
			for (File unit : filelist) {
				if (count == 0) {
					showlist.add(this.getResources().getText(
							R.string.lime_setting_select_root).toString());
					count++;
					continue;
				} else if (count == 1) {
					showlist.add(this.getResources().getText(
							R.string.lime_setting_select_parent).toString());
					count++;
					continue;
				}
				if (unit.isDirectory()) {
					showlist.add(" +[" + unit.getName() + "]");
				} else {
					showlist.add(" " + unit.getName());
				}
				count++;
			}

			// get a builder and set the view
			LayoutInflater li = LayoutInflater.from(this);
			View view = li.inflate(R.layout.filelist, null);

			ArrayAdapter<String> adapterlist = new ArrayAdapter<String>(this,
					R.layout.filerow, showlist);
			final String table = new String(tablename);
			ListView listview = (ListView) view.findViewById(R.id.list);
			
			listview.setAdapter(adapterlist);
			listview.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> arg0, View vi, int position,
						long id) {
					selectLimeFile(filelist.get(position).getAbsolutePath(), table);
				}
			});
			
			// if AlertDialog exists then dismiss before create
			if (ad != null) {
				ad.dismiss();
			}

			if (!//hasSelectFile) {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.lime_setting_btn_load_local_notice);
				builder.setView(view);
				builder.setNeutralButton(R.string.label_close_key,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dlg, int sumthin) {
							}
						});

				ad = builder.create();
				ad.show();
			}*/
			
		}


		public LIMESelectFileAdapter getAdapter(List<File> list) {
			return new LIMESelectFileAdapter(this, list);
		}

		public LIMESelectFileAdapter getAdapter(File path) {
			flist = getAvailableFiles(path.getAbsolutePath());
			return new LIMESelectFileAdapter(this, flist);
		}
		
		
		/**
		 * Get list of the file from the path
		 * 
		 * @param path
		 * @return
		 */
		private List<File> getAvailableFiles(String path) {

			List<File> templist = new ArrayList<File>();
			List<File> list = new ArrayList<File>();
			File check = new File(path);
			
			if (check.exists() && check.isDirectory()) {
				
				for(File f: check.listFiles()){
					if(f.canRead()){
						if(!f.isDirectory()){
							if( f.getName().toLowerCase().endsWith("cin") ||
								f.getName().toLowerCase().endsWith("lime") ||
								f.getName().toLowerCase().endsWith("txt")){
								list.add(f);
							}
						}else{
							list.add(f);
						}
					}
				}
				
				List<File> folders = new ArrayList<File>();
				List<File> files = new ArrayList<File>();
				for(File f: list){
					if(f.isDirectory()){
						folders.add(f);
					}else{
						files.add(f);
					}
				}
				
				List<File> result = new ArrayList<File>();
				Collections.sort(folders, SORT_FILENAME);
				Collections.reverse(folders);
				result.addAll(folders);
				Collections.sort(files, SORT_FILENAME);
				Collections.reverse(files);
				result.addAll(files);
				
				return result;

			} else { 
				//hasSelectFile = true;
				loadMapping(check);
				resetLabelInfo();
			}
			return templist;
		}

		static final Comparator<File> SORT_FILENAME = new Comparator<File>() {
		    public int compare(File e1, File e2) {
		    		return e2.getName().compareTo(e1.getName());
			}
	    };
	    
		/**
		 * Import mapping table into database
		 * 
		 * @param unit
		 */
		public void loadMapping(File unit) {
			try {
				DBSrv.loadMapping(unit.getAbsolutePath(), imtype);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			startLoadingWindow();
		}
		
		/*
		 * Show keyboard picker
		 */
		private void showKeyboardPicker() {

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setCancelable(true);
			builder.setIcon(R.drawable.sym_keyboard_done);
			builder.setNegativeButton(android.R.string.cancel, null);
			builder.setTitle(getResources().getString(R.string.keyboard_list));

			try {
				kblist = DBSrv.getKeyboardList();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			
			if(kblist != null){
				String curSelectKb = "";
				int curSelectKbPosition = 0;
				
				try {
					curSelectKb = DBSrv.getKeyboardCode(imtype);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				
				CharSequence[] items = new CharSequence[kblist.size()];
				for (int i = 0; i < kblist.size(); i++) {
					items[i] = kblist.get(i).getDescription();
					
					// Check if keyboard has been set, then record the position of item.
					if(kblist.get(i).getCode() != null &&
							curSelectKb != null && kblist.get(i).getCode().equals(curSelectKb)){
						curSelectKbPosition = i;
					}
				}
				
				builder.setSingleChoiceItems(items, curSelectKbPosition,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface di, int position) {
							di.dismiss();
							handlKeyboardSelection(position);
						}
				});
	
				mOptionsDialog = builder.create();
				//Window window = mOptionsDialog.getWindow();
				mOptionsDialog.show();
			}
		}
		
		private void handlKeyboardSelection(int position) {
			KeyboardObj kobj = kblist.get(position);
			try {
				DBSrv.setIMKeyboard(imtype, kobj.getDescription(), kobj.getCode());
				labKeyboard.setText(DBSrv.getImInfo(imtype, "keyboard"));
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			
		}

		private void startLoadingWindow(){
			Intent i = new Intent(this, LIMEMappingLoading.class);
			Bundle bundle = new Bundle();
	   		bundle.putString("keyboard", imtype);
	   		i.putExtras(bundle);
			startActivity(i);
		}
		
		private void createNavigationButtons(final File dir) {

			// Clean Top Area
			toplayout.removeAllViews();

			// Create Navigation Buttons
			String path = dir.getAbsolutePath();
			String[] pathlist = path.split("\\/");

			String pathconstruct = "/";
			if (pathlist.length > 0) {
				for (String p : pathlist) {
					if (!p.equals("") && !p.equals("/")) {
						pathconstruct += p + "/";
					} else {
						p = "/";
					}
					final String actpath = pathconstruct;
					Button b = new Button(this);
					b.setText(p);
					b.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
							LayoutParams.WRAP_CONTENT));
					b.setOnClickListener(new OnClickListener() {
						public void onClick(View arg0) {
							createNavigationButtons(new File(actpath));
							flist = getAvailableFiles(actpath);
							listview.setAdapter(getAdapter(flist));
						}
					});

					toplayout.addView(b);
				}
			} else {
				//final String actpath = pathconstruct;
				Button b = new Button(this);
				b.setText("/");
				b.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
						LayoutParams.WRAP_CONTENT));
				b.setOnClickListener(new OnClickListener() {
					public void onClick(View arg0) {
						createNavigationButtons(new File("/"));
						flist = getAvailableFiles("/");
						listview.setAdapter(getAdapter(flist));
					}
				});
				toplayout.addView(b);
				flist = getAvailableFiles("/");
				listview.setAdapter(getAdapter(flist));
			}
		}
		
		
}