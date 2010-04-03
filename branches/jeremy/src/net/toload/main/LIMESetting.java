package net.toload.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;

/**
 * 
 * @author Art Hung
 * 
 */
public class LIMESetting extends Activity {
	
	private final static boolean DEBUG = true;

	private final static String TOTAL_RECORD = "total_record";
	// Add by Jeremy '10, 3 ,27. Multi table extension.
	private final static String CJ_TOTAL_RECORD = "cj_total_record";
	private final static String BPMF_TOTAL_RECORD = "bpmf_total_record";
	private final static String DAYI_TOTAL_RECORD = "dayi_total_record";
	private final static String RELATED_TOTAL_RECORD = "related_total_record";
	//----------add by Jeremy '10,3,12 ----------------------------------------
	private final static String TOTAL_USERDICT_RECORD = "total_userdict_record";
	//-------------------------------------------------------------------------
	private final static String MAPPING_VERSION = "mapping_version";
	// Add by Jeremy '10, 3 ,27. Multi table extension.
	private final static String CJ_MAPPING_VERSION = "cj_mapping_version";
	private final static String BPMF_MAPPING_VERSION = "bmpf_mapping_version";
	private final static String DAYI_MAPPING_VERSION = "dayi_mapping_version";
	private final static String RELATED_MAPPING_VERSION = "related_mapping_version";
	private final static String MAPPING_LOADING = "mapping_loading";
	private final static String MAPPING_RESET = "mapping_reset";
	// Add by Jeremy '10, 3 ,27. Multi table extension.
	private final static String CJ_MAPPING_FILE_TEMP = "cj_mapping_file_temp";
	private final static String DAYI_MAPPING_FILE_TEMP = "dayi_mapping_file_temp";
	private final static String BPMF_MAPPING_FILE_TEMP = "bpmf_mapping_file_temp";
	private final static String MAPPING_FILE_TEMP = "mapping_file_temp";
	private final static String RELATED_FILE_TEMP = "related_file_temp";

	private ArrayList<File> filelist;

	private Button btnLoadSd;
	private Button btnLoadLocal;
	private Button btnReset;
	private Button btnResetDictionary;
	private Button btnBackup;
	private Button btnRestore;

	private AlertDialog ad;

	private String localRoot = "/sdcard/lime";
	private boolean hasSelectFile;

	private static LimeDB limedb;

	private ProgressDialog myDialog;

	private File mappingSrc;
	private TextView txtVersion;
	private TextView txtAmount;
	private TextView cjtxtVersion;
	private TextView cjtxtAmount;
	private TextView dayitxtVersion;
	private TextView dayitxtAmount;
	private TextView bpmftxtVersion;
	private TextView bpmftxtAmount;
	private TextView relatedtxtVersion;
	private TextView relatedtxtAmount;
	private TextView txtDictionaryAmount;
	private TextView txtMappingVersion;

	private Thread thread = null;
	private Resources res = null;
	private Context ctx = null;

	private IDBService DBSrv = null;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle icicle) {
		
		super.onCreate(icicle);
		
		this.setContentView(R.layout.setting);
		
		
		if (res == null) {
			res = this.getResources();
		}
		if (ctx == null) {
			ctx = this.getApplicationContext();
		}
		
		// Startup Service
		getApplicationContext().bindService(new Intent(IDBService.class.getName()), serConn, Context.BIND_AUTO_CREATE);
		
		// Add by Jeremy '10, 3, 27. reset loading status.
		ctx.getSharedPreferences(MAPPING_LOADING, 0).edit().putString(MAPPING_LOADING, "no").commit();
		
		// Get sdcard path from enviroment
		localRoot = Environment.getExternalStorageDirectory().getAbsolutePath() +"/lime" ;

		
		// Handle Load Mapping
		btnLoadLocal = (Button) this.findViewById(R.id.btnLoadLocal);
		btnLoadLocal.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				 // return if db is busy.
				SharedPreferences importset = ctx.getSharedPreferences(MAPPING_LOADING, 0);
				if( (importset.getString(MAPPING_LOADING, "no")).equals("yes")){
					Toast.makeText(v.getContext(), R.string.lime_setting_notification_db_busy, Toast.LENGTH_LONG).show();
					return;
				}
				hasSelectFile = false;
				showTablePicker(COMMAND_LOAD_TABLE);
				
				//selectLimeFile(localRoot);
			}
		});

		// Handle Reset Mapping
		btnReset = (Button) this.findViewById(R.id.btnReset);
		btnReset.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				/* Move to resetMapping()
				try {
				Toast.makeText(v.getContext(), R.string.lime_setting_notification_mapping_reset, Toast.LENGTH_LONG).show();	
				DBSrv.resetMapping();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				*/
				 // return if db is busy.
				SharedPreferences importset = ctx.getSharedPreferences(MAPPING_LOADING, 0);
				if( (importset.getString(MAPPING_LOADING, "no")).equals("yes")){
					Toast.makeText(v.getContext(), R.string.lime_setting_notification_db_busy, Toast.LENGTH_LONG).show();
					return;
				}
				showTablePicker(COMMAND_RESET_TABLE);
				updateInfomation();
			}
		});

		// Handle Reset Dictionary
		btnResetDictionary = (Button) this.findViewById(R.id.btnResetDictionary);
		btnResetDictionary.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// return if db is busy.
				SharedPreferences importset = ctx.getSharedPreferences(MAPPING_LOADING, 0);
				if( (importset.getString(MAPPING_LOADING, "no")).equals("yes")){
					Toast.makeText(v.getContext(), R.string.lime_setting_notification_db_busy, Toast.LENGTH_LONG).show();
					return;
				}
				try {
					Toast.makeText(v.getContext(), R.string.lime_setting_notification_userdic_reset, Toast.LENGTH_LONG).show();
					DBSrv.resetUserBackup();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				updateInfomation();
			}
		});

		// Backup Related Database
		btnBackup = (Button) this.findViewById(R.id.btnBackup);
		btnBackup.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				 // return if db is busy.
				SharedPreferences importset = ctx.getSharedPreferences(MAPPING_LOADING, 0);
				if( (importset.getString(MAPPING_LOADING, "no")).equals("yes")){
					Toast.makeText(v.getContext(), R.string.lime_setting_notification_db_busy, Toast.LENGTH_LONG).show();
					return;
				}
				try {
					Toast.makeText(v.getContext(), R.string.lime_setting_backup_message, Toast.LENGTH_LONG).show();
					DBSrv.executeUserBackup();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				backgroundUpdate();
			}
		});
		
		// Restore Related Database
		btnRestore = (Button) this.findViewById(R.id.btnRestore);
		btnRestore.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				 // return if db is busy.
				SharedPreferences importset = ctx.getSharedPreferences(MAPPING_LOADING, 0);
				if( (importset.getString(MAPPING_LOADING, "no")).equals("yes")){
					Toast.makeText(v.getContext(), R.string.lime_setting_notification_db_busy, Toast.LENGTH_LONG).show();
					return;
				}
				try {
					Toast.makeText(v.getContext(), R.string.lime_setting_restore_message, Toast.LENGTH_LONG).show();
					DBSrv.restoreRelatedUserdic();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				backgroundUpdate();
			}
		});
		
		// Update Information
		updateInfomation();

	}

	private void resetMapping(String tablename){
		try {
			Toast.makeText(this, R.string.lime_setting_notification_mapping_reset, Toast.LENGTH_LONG).show();	
			DBSrv.resetMapping(tablename);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
	}
	private ServiceConnection serConn = new ServiceConnection() {
		public void onServiceConnected(ComponentName name, IBinder service) {
			DBSrv = IDBService.Stub.asInterface(service);
		}
		public void onServiceDisconnected(ComponentName name) {}

	};
	
	
	private void backgroundUpdate(){
		if(thread != null){
			thread.stop();
			thread = null;
		}
		thread = new Thread() {
			public void run() {
				
				boolean dbbusy = true;
				
				while(dbbusy){
					try {
						Thread.sleep(1000);
						if(DEBUG){
							Log.i("Settings:backgrounUpdate", "dbbusy:"+dbbusy);
						}
						updateInfomation();
						if(ctx.getSharedPreferences(MAPPING_LOADING, 0)
								.getString(MAPPING_LOADING, "").equals("no")){
							dbbusy = false;
						}
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					if(DEBUG){
						Log.i("Settings:backgrounUpdate", "dbbusy:"+dbbusy);
					}
					updateInfomation();
					if(ctx.getSharedPreferences(MAPPING_LOADING, 0)
							.getString(MAPPING_LOADING, "").equals("no")){
						dbbusy = false;
					}
				}
				
				if(DEBUG){
					Log.i("Settings:backgrounUpdate", "end");
				}
			}
		};
		thread.start();
	}
	/**
	 * Update LIME setting panel display
	 */
	public void updateInfomation() {
		

		
		
		try {

			try {
				SharedPreferences settings = ctx.getSharedPreferences(TOTAL_RECORD, 0);
				String total = settings.getString(TOTAL_RECORD, "");
				SharedPreferences cjsettings = ctx.getSharedPreferences(CJ_TOTAL_RECORD, 0);
				String cjtotal = cjsettings.getString(CJ_TOTAL_RECORD, "");
				SharedPreferences dayisettings = ctx.getSharedPreferences(DAYI_TOTAL_RECORD, 0);
				String dayitotal = dayisettings.getString(DAYI_TOTAL_RECORD, "");
				SharedPreferences bpmfsettings = ctx.getSharedPreferences(BPMF_TOTAL_RECORD, 0);
				String bpmftotal = bpmfsettings.getString(BPMF_TOTAL_RECORD, "");
				SharedPreferences relatedsettings = ctx.getSharedPreferences(RELATED_TOTAL_RECORD, 0);
				String relatedtotal = relatedsettings.getString(RELATED_TOTAL_RECORD, "");
				
				
				txtAmount = (TextView) this.findViewById(R.id.txtInfoAmount);
				txtAmount.setText(total);
				cjtxtAmount = (TextView) this.findViewById(R.id.cjtxtInfoAmount);
				cjtxtAmount.setText(cjtotal);
				dayitxtAmount = (TextView) this.findViewById(R.id.dayitxtInfoAmount);
				dayitxtAmount.setText(dayitotal);
				bpmftxtAmount = (TextView) this.findViewById(R.id.bpmftxtInfoAmount);
				bpmftxtAmount.setText(bpmftotal);
				relatedtxtAmount = (TextView) this.findViewById(R.id.relatedtxtInfoAmount);
				relatedtxtAmount.setText(relatedtotal);
	
				
			} catch (Exception e) {
			}
			//
			String dictotal = null;
			try {
				// modified by Jeremy '10, 3,12
				//dictotal = limedb.countUserdic();
				SharedPreferences settings = ctx.getSharedPreferences(TOTAL_USERDICT_RECORD, 0);
				String recordString = settings.getString(TOTAL_USERDICT_RECORD, "0");
				dictotal = recordString;
			} catch (Exception e) {}

			String version = new String(""); 
			String cjversion=new String("");
			String dayiversion=new String("");
			String bpmfversion=new String("");
			String relatedversion=new String("");
			try {
				SharedPreferences settings = ctx.getSharedPreferences( MAPPING_VERSION, 0);
				version = settings.getString(MAPPING_VERSION, "");
				if(version == null || version.equals("")){
					SharedPreferences mappingtempset = ctx.getSharedPreferences(MAPPING_FILE_TEMP, 0);
					version = mappingtempset.getString(MAPPING_FILE_TEMP, "");
				}
				txtVersion = (TextView) this.findViewById(R.id.txtInfoVersion);
				txtVersion.setText(version);
				
				SharedPreferences cjsettings = ctx.getSharedPreferences( CJ_MAPPING_VERSION, 0);
				cjversion = cjsettings.getString(CJ_MAPPING_VERSION, "");
				if(cjversion == null || cjversion.equals("")){
					SharedPreferences mappingtempset = ctx.getSharedPreferences(CJ_MAPPING_FILE_TEMP, 0);
					cjversion = mappingtempset.getString(CJ_MAPPING_FILE_TEMP, "");
				}
				cjtxtVersion = (TextView) this.findViewById(R.id.cjtxtInfoVersion);
				cjtxtVersion.setText(cjversion);
				
				SharedPreferences dayisettings = ctx.getSharedPreferences( DAYI_MAPPING_VERSION, 0);
				dayiversion = dayisettings.getString(DAYI_MAPPING_VERSION, "");
				if(dayiversion == null || dayiversion.equals("")){
					SharedPreferences mappingtempset = ctx.getSharedPreferences(DAYI_MAPPING_FILE_TEMP, 0);
					dayiversion = mappingtempset.getString(DAYI_MAPPING_FILE_TEMP, "");
				}
				dayitxtVersion = (TextView) this.findViewById(R.id.dayitxtInfoVersion);
				dayitxtVersion.setText(dayiversion);
				
				SharedPreferences bpmfsettings = ctx.getSharedPreferences( BPMF_MAPPING_VERSION, 0);
				bpmfversion = bpmfsettings.getString(BPMF_MAPPING_VERSION, "");
				if(bpmfversion == null || bpmfversion.equals("")){
					SharedPreferences mappingtempset = ctx.getSharedPreferences(BPMF_MAPPING_FILE_TEMP, 0);
					bpmfversion = mappingtempset.getString(BPMF_MAPPING_FILE_TEMP, "");
				}
				bpmftxtVersion = (TextView) this.findViewById(R.id.bpmftxtInfoVersion);
				bpmftxtVersion.setText(bpmfversion);
				
				SharedPreferences relatedsettings = ctx.getSharedPreferences( RELATED_MAPPING_VERSION, 0);
				relatedversion = relatedsettings.getString(RELATED_MAPPING_VERSION, "");
				if(relatedversion == null || relatedversion.equals("")){
					SharedPreferences mappingtempset = ctx.getSharedPreferences(RELATED_FILE_TEMP, 0);
					relatedversion = mappingtempset.getString(RELATED_FILE_TEMP, "");
				}
				relatedtxtVersion = (TextView) this.findViewById(R.id.relatedtxtInfoVersion);
				relatedtxtVersion.setText(relatedversion);
			} catch (Exception e) {e.printStackTrace();}

			limedb = new LimeDB(this);
			

			txtDictionaryAmount = (TextView) this
					.findViewById(R.id.txtInfoDictionaryAmount);
			// modified by Jeremy '10, 3,12
			//txtDictionaryAmount.setText(String.valueOf(dictotal));
			txtDictionaryAmount.setText(dictotal);
			
			

			//this.findViewById(R.id.SettingsView).forceLayout();
			this.findViewById(R.id.SettingsView).invalidate();
		
		
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Select file to be import from the list
	 * 
	 * @param path
	 */
	public void selectLimeFile(String path, String tablename) {

		// Retrieve Filelist
		filelist = getAvailableFiles(path, tablename);

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

		if (!hasSelectFile) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.lime_setting_btn_load_local_notice);
			builder.setView(view);
			builder.setNeutralButton(R.string.lime_setting_btn_close,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dlg, int sumthin) {

						}
					});

			ad = builder.create();
			ad.show();
		}
	}

	/**
	 * Get list of the file from the path
	 * 
	 * @param path
	 * @return
	 */
	private ArrayList<File> getAvailableFiles(String path, String tablename) {

		ArrayList<File> templist = new ArrayList<File>();

		File check = new File(path);

		if (check.exists() && check.isDirectory()) {

			File root = null;
			root = new File(localRoot);

			// Fixed first 1 & 2
			// Root
			templist.add(root);

			// Back to Parent
			if (check.getParentFile().getAbsolutePath().equals("/")) {
				templist.add(root);
			} else {
				templist.add(check.getParentFile());
			}

			File rootPath = new File(path);
			File list[] = rootPath.listFiles();
			for (File unit : list) {
				if (unit.isDirectory()
						|| (unit.isFile() && unit.getName().toLowerCase().endsWith(".lime"))
						|| (unit.isFile() && unit.getName().toLowerCase().endsWith(".cin"))) {
					templist.add(unit);
				}
			}

		} else if (check.exists() && check.isFile()
				&& ( check.getName().toLowerCase().endsWith(".lime") || check.getName().toLowerCase().endsWith(".cin"))  ) {

			hasSelectFile = true;
			
			loadMapping(check,tablename);
		
		
		}
		return templist;
	}

	private AlertDialog mTableDialog;
	/**
	 * Import mapping table into database
	 * 
	 * @param unit
	 */
	public void loadMapping(File unit, String tablename) {

		try {
			Toast.makeText(this, R.string.lime_setting_notification_db_loading, Toast.LENGTH_LONG).show();
			DBSrv.loadMapping(unit.getAbsolutePath(), tablename);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		
	}

	/**
	 * Handle myDialog activity
	 */
	private Handler uiCallback = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			updateInfomation();
			if (myDialog != null) {
				myDialog.dismiss();
			}
		}
	};

	public class EmptyListener implements
			android.content.DialogInterface.OnClickListener {
		public void onClick(DialogInterface v, int buttonId) {

		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPause()
	 */
	@Override
	protected void onPause() {
		if(thread != null){
			thread.suspend();
		}
		super.onPause();
		uiCallback.sendEmptyMessage(0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onRestart()
	 */
	@Override
	protected void onRestart() {
		// TODO Auto-generated method stub
		if(thread != null){super.onRestart();}
		thread.resume();
	}
	
	private final int COMMAND_LOAD_TABLE = 0;
	private final int COMMAND_RESET_TABLE = 1;
    /**
     * Add by Jeremy '10, 3, 24 for keyboard picker menu in options menu
     */
    private void showTablePicker(final int command){
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setIcon(R.drawable.sym_keyboard_done);
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setTitle(getResources().getString(R.string.table_list));
        
        final CharSequence[] items = getResources().getStringArray(R.array.table);
        
        builder.setSingleChoiceItems(
        		items, 
        		-1, 
        		new DialogInterface.OnClickListener() {
        			
			public void onClick(DialogInterface di, int position) {
                di.dismiss();
            	
                String tablename = new String("mapping");
                
                switch(position){
                case 0:
            		tablename = "mapping";
            		break;
                case 1:
                	tablename = "cj";
                	break;
                case 2:
                	tablename = "dayi";
                	break;
                case 3:
                	tablename = "phonetic";
                	break;
                case 4:
                	tablename = "related";
                	break;
                } 
                switch(command){
                case COMMAND_LOAD_TABLE:
                	selectLimeFile(localRoot, tablename);
                	//backgroundUpdate();
                	break;
                case COMMAND_RESET_TABLE:
                	resetMapping(tablename);
                	//backgroundUpdate();
                
                	break;
                }
            }
        });
        
        mTableDialog = builder.create();
        mTableDialog.show();
        
    }

}