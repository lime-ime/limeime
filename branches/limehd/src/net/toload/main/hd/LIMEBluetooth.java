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
import net.toload.main.hd.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.os.RemoteException;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
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
public class LIMEBluetooth extends Activity {
	
	private BluetoothAdapter mBluetoothAdapter = null;
    
	private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    
	private EditText edtBtStatus;
	private EditText edtDeviceName;
	private EditText edtDeviceMac;
	
    private static final int REQUEST_ENABLE_BT = 3;
    // Name for the SDP record when creating server socket 
    private static final String NAME_SECURE = "BluetoothKBSecure"; 
    private static final String NAME_INSECURE = "BluetoothKBInsecure";
    
    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE = UUID.fromString("00001124-0000-1000-8000-00805F9B34FB");
    private static final UUID MY_UUID_INSECURE = UUID.fromString("00001124-0000-1000-8000-00805F9B34FB");
    
    private int mState; 
    
    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;
    // we're doing nothing   
    public static final int STATE_LISTEN = 1;
    // now listening for incoming connections   
    public static final int STATE_CONNECTING = 2; 
    // now initiating an outgoing connection   
    public static final int STATE_CONNECTED = 3; 
    // now connected to a remote device
    
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle icicle) {
		
		super.onCreate(icicle);
		this.setContentView(R.layout.bluetooth);
		
		edtBtStatus = (EditText) findViewById(R.id.btStatus);
		edtDeviceName = (EditText) findViewById(R.id.edtDeviceName);
		edtDeviceMac = (EditText) findViewById(R.id.edtDeviceMac);
		Button btnDiscoveryBT = (Button) findViewById(R.id.btnDiscoveryBT);
		Button btnInitialDevice = (Button) findViewById(R.id.btnInitialDevice);
		Button btnConnectDevice = (Button) findViewById(R.id.btnConnectDevice);
		
        // Get local Bluetooth adapter        
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		
		// If the adapter is null, then Bluetooth is not supported        
		if (mBluetoothAdapter == null) {            
			Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
			edtBtStatus.setText("Bluetooth is not available");
		}else{          
			Toast.makeText(this, "Great !! Bluetooth is available", Toast.LENGTH_LONG).show();
			edtBtStatus.setText("Bluetooth is available");
		}
		
		btnDiscoveryBT.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Log.i("ART","Click to discovery device");
				edtBtStatus.setText("Click to discovery device");
				// Create a BroadcastReceiver for ACTION_FOUND

				doDiscovery();
				
			}
		});

		
		btnConnectDevice.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Log.i("ART","Click to connect device");
				BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(String.valueOf(edtDeviceMac.getText()).trim() );
				Method m;
				try {
					m = device.getClass().getMethod("createRfcommSocket", new Class[] {int.class});

					BluetoothSocket socket = (BluetoothSocket) m.invoke(device, 1);
					socket.connect();
				} catch (Exception e) {
					e.printStackTrace();
				}

				connect(device, true);
			}
		});

		btnInitialDevice.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				
				Log.i("ART","Initial Bluetooth Device");        
				BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(String.valueOf(edtDeviceMac.getText()).trim() );
				
				
				Method m;
				try {
					m = device.getClass().getMethod("createRfcommSocket", new Class[] {int.class});

					BluetoothSocket socket = (BluetoothSocket) m.invoke(device, 1);
					socket.connect();
				} catch (Exception e) {
					e.printStackTrace();
				}
				//connect(device, false);
				
				 // Cancel any thread attempting to make a connection
				if (mConnectThread != null) {
					mConnectThread.cancel(); 
					mConnectThread = null;
				}       
				
				// Cancel any thread currently running a connection    
				if (mConnectedThread != null) {
					mConnectedThread.cancel(); 
					mConnectedThread = null;
				}      
				
				setState(STATE_LISTEN);  
				
				// Start the thread to listen on a BluetoothServerSocket  
				if (mSecureAcceptThread == null) { 
					mSecureAcceptThread = new AcceptThread(true);  
					mSecureAcceptThread.start();   
				}      
				if (mInsecureAcceptThread == null) {  
					mInsecureAcceptThread = new AcceptThread(false);  
					mInsecureAcceptThread.start();     
				}
				
				/*Class cl;
				try {
					cl = Class.forName("android.bluetooth.BluetoothDevice");
					 Class[] par = {};
					 Method method = cl.getMethod("getUuids", par);
					 Object[] args = {};
					 ParcelUuid[] retval = (ParcelUuid[])method.invoke(device, args);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}*/
				
				/*try {
					//Log.i("ART","1234->Start");
					//BluetoothSocket tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
					//String a = device.getUuids();
					//tmp.connect();
					//connected(tmp, device, "Secured");
					//Log.i("ART","1234->After");
				} catch (Exception e) {
					e.printStackTrace();
				}
				*/
				 
				
			}
		});

		
        // Register for broadcasts when discovery has finished
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		registerReceiver(mReceiver, filter); 
		
        // Register for broadcasts when discovery has finished 
		filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);  
		registerReceiver(mReceiver, filter);
		
		//String action = "android.bleutooth.device.action.UUID";
		//registerReceiver(mReceiver, filter);
		  
		 // Get a set of currently paired devices   
		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();    
		// If there are paired devices, add each one to the ArrayAdapter     
		if (pairedDevices.size() > 0) {         
			//findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);    
			for (BluetoothDevice device : pairedDevices) { 
				Log.i("ART","Paried Device : " + device.getName() + ", " + device.getAddress());
				edtBtStatus.setText("Paried Device : " + device.getName() + ", " + device.getAddress());
				//mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress()); 
				
			}
			
		}else {   
			Log.i("ART","No currently paried device.");
			edtBtStatus.setText("No currently paried device.");
				//String noDevices = getResources().getText(R.string.none_paired).toString();   
				//mPairedDevicesArrayAdapter.add(noDevices);   
		}
		
		
		
/*
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			// Device does not support Bluetooth
		}
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, this.REQUEST_ENABLE_BT);
		}
		
		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
		// If there are paired devices
		if (pairedDevices.size() > 0) {    
			// Loop through paired devices    
			for (BluetoothDevice device : pairedDevices) {        
				// Add the name and address to an array adapter to show in a ListView        
				//mArrayAdapter.add(device.getName() + "\n" + device.getAddress());    }}
			}
		}
		
		// Create a BroadcastReceiver for ACTION_FOUND
		mReceiver = new BroadcastReceiver() {    
			public void onReceive(Context context, Intent intent) {    
				
				String action = intent.getAction();        
				// When discovery finds a device        
				if (BluetoothDevice.ACTION_FOUND.equals(action)) {            
					// Get the BluetoothDevice object from the Intent            
					BluetoothDevice device = intent.getParcelableExtra(
							BluetoothDevice.EXTRA_DEVICE);            
					// Add the name and address to an array adapter to show in a ListView            
					//mArrayAdapter.add(device.getName() + "\n" + device.getAddress());        }    }};
					// Register the BroadcastReceiver
					IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
					registerReceiver(mReceiver, filter); 
					// Don't forget to unregister during onDestroy
				}
			}
		};
		
		// Register the BroadcastReceiver
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
		
		Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
		startActivity(discoverableIntent);*/
		
		
	}
	
    @Override    
    protected void onDestroy() {  
    	
    	super.onDestroy(); 
    	
    	// Make sure we're not doing discovery anymore 
    	if (mBluetoothAdapter != null) {        
    		mBluetoothAdapter.cancelDiscovery();   
    	}     
    	
    	// Unregister broadcast listeners       
    	this.unregisterReceiver(mReceiver); 
    }
	
	@Override   
	public void onStart() {
		super.onStart();  
		
		// If BT is not on, request that it be enabled. 
		// setupChat() will then be called during onActivityResult  
		if (!mBluetoothAdapter.isEnabled()) {  
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);  
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);     
			// Otherwise, setup the chat session    
		} else {          
			//if (mChatService == null) setupChat();   
		} 
	}
	
	
	 private void doDiscovery() { 
			Log.i("ART","Do Discovery");
		 // Indicate scanning in the title     
		 setProgressBarIndeterminateVisibility(true);   
		 // If we're already discovering, stop it     
		 if (mBluetoothAdapter.isDiscovering()) {       
			 mBluetoothAdapter.cancelDiscovery();    
			 }      
		 // Request discover from BluetoothAdapter    
		 mBluetoothAdapter.startDiscovery();  
	 }
	
	
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {    
		public void onReceive(Context context, Intent intent) {  
			
			//BluetoothDevice deviceExtra = intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
			//Parcelable[] uuidExtra = intent.getParcelableArrayExtra("android.bluetooth.device.extra.UUID");
			    //Parse the UUIDs and get the one you are interested in
			
			
			//Log.i("ART","On Receive ... " + uuidExtra.toString()); 
			Log.i("ART","On Receive ... ");  
			
			String action = intent.getAction();        
			// When discovery finds a device     
			Log.i("ART","action :" + action);     
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {     

				Log.i("ART","ACTION_FOUND");  
				
				// Get the BluetoothDevice object from the Intent            
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);            
				// Add the name and address to an array adapter to show in a ListView            
				//mArrayAdapter.add(device.getName() + "\n" + device.getAddress());        }    }};
				// Register the BroadcastReceiver
				IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
				registerReceiver(mReceiver, filter); 
				
				edtDeviceName.setText(device.getName());
				edtDeviceMac.setText(device.getAddress());
				Log.i("ART",device.getName() + " / " + device.getAddress());
				// Don't forget to unregister during onDestroy
			}
		}
	};
	
	
	/**     
	 * * Set the current state of the chat connection    
	 *  * @param state  An integer defining the current connection state 
	 *      */   
	private synchronized void setState(int state) {   
	//	if (D) Log.d(TAG, "setState() " + mState + " -> " + state); 
		mState = state;      
		// Give the new state to the Handler so the UI Activity can update   
		//mHandler.obtainMessage(BluetoothChat.MESSAGE_STATE_CHANGE, state, -1).sendToTarget(); 
	}
	
	/**     
	 * * Start the ConnectThread to initiate a connection to a remote device.  
	 *    * @param device  The BluetoothDevice to connect  
	 *       * @param secure Socket Security type - Secure (true) , Insecure (false)  
	 *          */   
	public synchronized void connect(BluetoothDevice device, boolean secure) { 
		
		
		// Cancel any thread currently running a connection   
		if (mConnectedThread != null) {
			mConnectedThread.cancel(); 
			mConnectedThread = null;
		} 
		
		// Start the thread to connect with the given device    
		mConnectThread = new ConnectThread(device, secure); 
		mConnectThread.start();      
		setState(STATE_CONNECTING); 
	}
	
	/**    
	 *  * Start the ConnectedThread to begin managing a Bluetooth connection   
	 *    * @param socket  The BluetoothSocket on which the connection was made 
   * @param device  The BluetoothDevice that has been connected    
   *  */   
	public synchronized void connected(BluetoothSocket socket, BluetoothDevice     
			device, final String socketType) {   
		
		// Cancel the thread that completed the connection      
		if (mConnectThread != null) {
			mConnectThread.cancel(); 
			mConnectThread = null;
		}      
		
		// Cancel any thread currently running a connection     
		if (mConnectedThread != null) {
			mConnectedThread.cancel(); 
			mConnectedThread = null;
		}        
		
		// Cancel the accept thread because we only want to connect to one device 
		if (mSecureAcceptThread != null) {           
			mSecureAcceptThread.cancel();  
			mSecureAcceptThread = null; 
		}        
		
		if (mInsecureAcceptThread != null) { 
			mInsecureAcceptThread.cancel();  
			mInsecureAcceptThread = null;   
		}  
		
		// Start the thread to manage the connection and perform transmissions  
		mConnectedThread = new ConnectedThread(socket, socketType); 
		mConnectedThread.start();      
		
		// Send the name of the connected device back to the UI Activity 
		/*Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_DEVICE_NAME); 
		Bundle bundle = new Bundle();     
		bundle.putString(BluetoothChat.DEVICE_NAME, device.getName()); 
		msg.setData(bundle);      
		mHandler.sendMessage(msg); */  
		
		setState(STATE_CONNECTED);   
	}   
	
	/**    
	 *  * Stop all threads 
	 *      */   
	public synchronized void stop() { 
		if (mConnectThread != null) { 
			mConnectThread.cancel();     
			mConnectThread = null;      
			}       
		if (mConnectedThread != null) {  
			mConnectedThread.cancel(); 
			mConnectedThread = null;    
			}       
		if (mSecureAcceptThread != null) {  
			mSecureAcceptThread.cancel();  
			mSecureAcceptThread = null;   
			}      
		if (mInsecureAcceptThread != null) {  
			mInsecureAcceptThread.cancel();  
			mInsecureAcceptThread = null;    
		}
		
		setState(STATE_NONE);  
	}
	
	/**    
	 *  * Write to the ConnectedThread in an unsynchronized manner  
	 *     * @param out The bytes to write  
	 *        * @see ConnectedThread#write(byte[]) 
	 *            */   
	public void write(byte[] out) { 
		
		// Create temporary object    
		ConnectedThread r;      
		// Synchronize a copy of the ConnectedThread 
		synchronized (this) {          
			if (mState != STATE_CONNECTED) 
				return;  
			r = mConnectedThread;      
		}      
		
		// Perform the write unsynchronized    
		r.write(out);  
	}
	
	/**   
	 *   * Indicate that the connection attempt failed and notify the UI Activity.  
	 *      */ 
	private void connectionFailed() {   
		// Send a failure message back to the Activity    
/*		Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);   
		Bundle bundle = new Bundle();      
		bundle.putString(BluetoothChat.TOAST,
				"Unable to connect device");      
		msg.setData(bundle);       
		mHandler.sendMessage(msg);   
		// Start the service over to restart listening mode  
		BluetoothChatService.this.start();  
 		*/		
	}
	
	/**     
	 * * Indicate that the connection was lost and notify the UI Activity. 
	 *     */  
	private void connectionLost() {   
		// Send a failure message back to the Activity  
		/*Message msg = mHandler.obtainMessage(BluetoothChat.MESSAGE_TOAST);  
		Bundle bundle = new Bundle(); 
		bundle.putString(BluetoothChat.TOAST, "Device connection was lost");  
		msg.setData(bundle);        
		mHandler.sendMessage(msg);    
		// Start the service over to restart listening mode   
		BluetoothChatService.this.start();  */  
	}
	
    	
	/**     
	 * * This thread runs while listening for incoming connections. It behaves     
	 * * like a server-side client. It runs until a connection is accepted     
	 * * (or until cancelled).     
	 * */    
	class AcceptThread extends Thread {   
		
		// The local server socket        
		private final BluetoothServerSocket mmServerSocket;   
		
		private String mSocketType;
		
		public AcceptThread(boolean secure) {    

			Log.i("ART", "AcceptThread: Start");          
			BluetoothServerSocket tmp = null;            
			mSocketType = secure ? "Secure":"Insecure";  
			
			// Create a new listening server socket            
			try {                
				if (secure) {                    
					tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,MY_UUID_SECURE);                
				} else {
					tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, MY_UUID_INSECURE);
				}          
				Log.i("ART", "AcceptThread Secure Type : " + secure);     
			} catch (IOException e) { 
				Log.e("ART", "Socket Type: " + mSocketType + "listen() failed", e); 
			}           
			
			mmServerSocket = tmp;
			Log.i("ART", "AcceptThread: initialized... " + tmp);  
		}
		
		public void run() { 
  
    	   
    	   BluetoothSocket socket = null;        

			Log.i("ART", "AcceptThread: running... " + socket);  
    	   // Listen to the server socket if we're not connected     
    	   while (true) {   
    		   try {                    
    			   // This is a blocking call and will only return on a   
    			   // successful connection or an exception   
    				Log.i("ART", "AcceptThread: listening... " + socket);       
    			   socket = mmServerSocket.accept();     
    			   Log.i("ART", "AcceptThread: Accept! ");          
    		   } catch (IOException e) {            
    			   Log.e("ART", "AcceptThread Socket Type: " + mSocketType + "accept() failed", e);   
    				break;        
    		   }       
    		   
    		   try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    		
    		   // If a connection was accepted    
    			if (socket != null) {    
    				
    				Log.i("ART","######->"+socket);
					Log.i("ART", "AcceptThread State Connecting...");  
				   connected(socket, socket.getRemoteDevice(),  mSocketType);    
    				Log.i("ART", "AcceptThread: Connected! ");           
    				/*
    				switch (mState) {                    
    				   case STATE_LISTEN:                   
    				   case STATE_CONNECTING:       
    					  // Situation normal. Start the connected thread.  
							Log.e("ART", "AcceptThread State Connecting...");  
    					   connected(socket, socket.getRemoteDevice(),  mSocketType);    
    	    				Log.i("ART", "AcceptThread: Connected! ");                     
    					  break;                     
    				   case STATE_NONE: 
    				   case STATE_CONNECTED:    
    						// Either not ready or already connected. Terminate new socket.
    						try {                
    							socket.close();  
    						} catch (IOException e) {  
    							Log.e("ART", "Could not close unwanted socket", e);  
    						}                         
    						break;   
    				}*/
    			}      
    	   }
		}
		
    			
       public void cancel() {         
    		/*try {             
    			   //mmServerSocket.close();     
    			   Log.i("ART", "AcceptThread: close! ");   
    		} catch (IOException e) {   
    			Log.e("ART", "Socket Type" + mSocketType + "close() of server failed", e);  
    		} */      
       }
       
	}
	
	
	
	/**     
	 * * This thread runs while attempting to make an outgoing connection     
	 * * with a device. It runs straight through; the connection either    
	 *  * succeeds or fails.     
	 *  */   
	class ConnectThread extends Thread { 
		
		private final BluetoothSocket mmSocket;    
		private final BluetoothDevice mmDevice;     
		private String mSocketType;       
		
		public ConnectThread(BluetoothDevice device, boolean secure) {  
			
			mmDevice = device;          
			BluetoothSocket tmp = null;    
			mSocketType = secure ? "Secure" : "Insecure";   
			
			// Get a BluetoothSocket for a connection with the  
			// given BluetoothDevice        
			try {              
				if (secure) {   
					tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
					Log.i("ART", "secure mmSocket" + MY_UUID_SECURE);               
				} else {            
					tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);  
					Log.i("ART", "insecure mmSocket" + MY_UUID_INSECURE);           
				}
				Log.i("ART", "tmp -> " + tmp);               
				
			} catch (IOException e) {  
				Log.e("ART", "Socket Type: " + mSocketType + "create() failed", e);    
			}          
			
			mmSocket = tmp;     
			Log.i("ART", "mmSocket" + mmSocket);  
		}
		
		public void run() {     
			
			Log.i("ART", "BEGIN mConnectThread SocketType:" + mSocketType);  
			setName("ConnectThread" + mSocketType);
			
			// Always cancel discovery because it will slow down a connection 
			mBluetoothAdapter.cancelDiscovery();       
			
			// Make a connection to the BluetoothSocket   
			try {               
				// This is a blocking call and will only return on a     
				// successful connection or an exception  
				Log.i("ART", "Start Connection ConnectThread" + mmSocket);             
				mmSocket.connect();      
				Log.i("ART", "Finish Connection ConnectThread" + mmSocket);  
			} catch (IOException e) { 
				// Close the socket          
				try {          
					Log.i("ART", "Error then close..." + e);     
					mmSocket.close();  
				} catch (IOException e2) {  
					Log.e("ART", "unable to close() " + mSocketType + " socket during connection failure", e2);   
				}               
				connectionFailed(); 
				return;        
			}            
				
			// Reset the ConnectThread because we're done   
			//synchronized (BluetoothChatService.this) {   
			//	mConnectThread = null;    
			//}           
			
			// Start the connected thread   
			connected(mmSocket, mmDevice, mSocketType);
		}       
		
		public void cancel() { 
				try {   
					mmSocket.close(); 
				} catch (IOException e) {     
					Log.e("ART", "close() of connect " + mSocketType + " socket failed", e); 
				}      
         }
		
	}
	
	/**     
	 * * This thread runs during a connection with a remote device. 
	 *     * It handles all incoming and outgoing transmissions.   
	 *       */  
	class ConnectedThread extends Thread {
		
		private final BluetoothSocket mmSocket;  
		private final InputStream mmInStream;     
		private final OutputStream mmOutStream;     
		
		public ConnectedThread(BluetoothSocket socket, String socketType) {  
			
			Log.d("ART", "create ConnectedThread: " + socketType);   
			mmSocket = socket;         
			
			InputStream tmpIn = null;     
			OutputStream tmpOut = null;        
			
			// Get the BluetoothSocket input and output streams  
			try {            
			   tmpIn = socket.getInputStream();    
			   tmpOut = socket.getOutputStream();         
		    } catch (IOException e) {           
			   Log.e("ART", "temp sockets not created", e);  
			}
		    
		   mmInStream = tmpIn;    
		   mmOutStream = tmpOut;     
		}
		
		
		public void run() {     
			Log.i("ART", "BEGIN mConnectedThread");   
			
			byte[] buffer = new byte[1024];
			
			int bytes;           
			// Keep listening to the InputStream while connected 

			Log.e("ART", "Start managed connection");  
			while (true) {          
				//try {       
					// Read from the InputStream  
					try {
						bytes = mmInStream.read(buffer);
						Log.i("ART", "->"+(char)bytes);   
					} catch (IOException e) {
						//e.printStackTrace();
						break;
					}      
					// Send the obtained bytes to the UI Activity     
					/*mHandler.obtainMessage(BluetoothChat.MESSAGE_READ, bytes, -1, buffer)   
					.sendToTarget();         
					} catch (IOException e) {  
						Log.e(TAG, "disconnected", e); 
						connectionLost();               
						// Start the service over to restart listening mode   
						BluetoothChatService.this.start();     
						break;             
						}        
					} */ 

			}   
		}
		
		/**      
		 *    
		 *    * Write to the connected OutStream.    
		 *         * @param buffer  The bytes to write   
		 *               */  
		public void write(byte[] buffer) {   
			try {            
				Log.e("ART", "mmOutStream write"); 
				mmOutStream.write(buffer);     
				// Share the sent message back to the UI Activity     
				/*mHandler.obtainMessage(BluetoothChat.MESSAGE_WRITE, -1, -1, buffer)  
				.sendToTarget();    */
			} catch (IOException e) { 
				Log.e("ART", "Exception during write", e); 
			}       
		}  
		
		public void cancel() {  
			try {            
				Log.e("ART", "mmSocket close");  
				mmSocket.close();  
			} catch (IOException e) {  
				Log.e("ART", "close() of connect socket failed", e);    
			} 	
		}
		
	}

}


