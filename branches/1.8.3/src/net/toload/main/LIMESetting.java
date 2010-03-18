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

	private final static String TOTAL_RECORD = "total_record";
	//----------add by Jeremy '10,3,12 ----------------------------------------
	private final static String TOTAL_USERDICT_RECORD = "total_userdict_record";
	//-------------------------------------------------------------------------
	private final static String MAPPING_VERSION = "mapping_version";
	private final static String MAPPING_LOADING = "mapping_loading";
	private final static String MAPPING_RESET = "mapping_reset";
	private final static String MAPPING_FILE_TEMP = "mapping_file_temp";

	private ArrayList<File> filelist;

	private Button btnLoadSd;
	private Button btnLoadLocal;
	private Button btnReset;
	private Button btnResetDictionary;
	private Button btnBackup;
	private Button btnRestore;

	private AlertDialog ad;

	private String localRoot = "/sdcard";
	private boolean hasSelectFile;

	private static LimeDB limedb;

	private ProgressDialog myDialog;

	private File mappingSrc;
	private TextView txtVersion;
	private TextView txtAmount;
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
		
		// Handle Load Mapping
		btnLoadLocal = (Button) this.findViewById(R.id.btnLoadLocal);
		btnLoadLocal.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				 hasSelectFile = false; 
				 selectLimeFile(localRoot);
			}
		});

		// Handle Reset Mapping
		btnReset = (Button) this.findViewById(R.id.btnReset);
		btnReset.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				try {
					Toast.makeText(v.getContext(), R.string.lime_setting_notification_mapping_reset, Toast.LENGTH_LONG).show();
					DBSrv.resetMapping();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				updateInfomation();
			}
		});

		// Handle Reset Dictionary
		btnResetDictionary = (Button) this.findViewById(R.id.btnResetDictionary);
		btnResetDictionary.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
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
				try {
					Toast.makeText(v.getContext(), R.string.lime_setting_backup_message, Toast.LENGTH_LONG).show();
					DBSrv.executeUserBackup();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				updateInfomation();
			}
		});
		
		// Restore Related Database
		btnRestore = (Button) this.findViewById(R.id.btnRestore);
		btnRestore.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				try {
					Toast.makeText(v.getContext(), R.string.lime_setting_restore_message, Toast.LENGTH_LONG).show();
					DBSrv.restoreRelatedUserdic();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				updateInfomation();
			}
		});
		
		// Update Information
		updateInfomation();

	}

	private ServiceConnection serConn = new ServiceConnection() {
		public void onServiceConnected(ComponentName name, IBinder service) {
			DBSrv = IDBService.Stub.asInterface(service);
		}
		public void onServiceDisconnected(ComponentName name) {}

	};
	
	/**
	 * Update LIME setting panel display
	 */
	public void updateInfomation() {
		
		try {

			try {
				SharedPreferences settings = ctx.getSharedPreferences(
						TOTAL_RECORD, 0);
				String recordString = settings.getString(TOTAL_RECORD, "");

				String total = recordString;
				txtAmount = (TextView) this.findViewById(R.id.txtInfoAmount);
				txtAmount.setText(total);
			} catch (Exception e) {
			}
			//
			String dictotal = null;
			try {
				// modified by Jeremy '10, 3,12
				//dictotal = limedb.countUserdic();
				SharedPreferences settings = ctx.getSharedPreferences(
						TOTAL_USERDICT_RECORD, 0);
				String recordString = settings.getString(TOTAL_USERDICT_RECORD, "0");
				dictotal = recordString;
			} catch (Exception e) {
			}

			String version = "";
			try {
				SharedPreferences settings = ctx.getSharedPreferences( MAPPING_VERSION, 0);
				version = settings.getString(MAPPING_VERSION, "");
				if(version == null || version.equals("")){
					SharedPreferences mappingtempset = ctx.getSharedPreferences(MAPPING_FILE_TEMP, 0);
					version = mappingtempset.getString(MAPPING_FILE_TEMP, "");
				}
				txtVersion = (TextView) this.findViewById(R.id.txtInfoVersion);
				txtVersion.setText(version);
			} catch (Exception e) {}

			limedb = new LimeDB(this);
			

			txtDictionaryAmount = (TextView) this
					.findViewById(R.id.txtInfoDictionaryAmount);
			// modified by Jeremy '10, 3,12
			//txtDictionaryAmount.setText(String.valueOf(dictotal));
			txtDictionaryAmount.setText(dictotal);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Select file to be import from the list
	 * 
	 * @param path
	 */
	public void selectLimeFile(String path) {

		// Retrieve Filelist
		filelist = getAvailableFiles(path);

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
		ListView listview = (ListView) view.findViewById(R.id.list);
		listview.setAdapter(adapterlist);
		listview.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg0, View vi, int position,
					long id) {
				selectLimeFile(filelist.get(position).getAbsolutePath());
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
	private ArrayList<File> getAvailableFiles(String path) {

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
						|| (unit.isFile() && unit.getName().toLowerCase()
								.endsWith(".lime"))) {
					templist.add(unit);
				}
			}

		} else if (check.exists() && check.isFile()
				&& check.getName().toLowerCase().endsWith(".lime")) {

			hasSelectFile = true;
			loadMapping(check);
		
		}
		return templist;
	}

	/**
	 * Import mapping table into database
	 * 
	 * @param unit
	 */
	public void loadMapping(File unit) {

		try {
			Toast.makeText(this, R.string.lime_setting_notification_db_loading, Toast.LENGTH_LONG).show();
			DBSrv.loadMapping(unit.getAbsolutePath());
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
		super.onRestart();
	}

}