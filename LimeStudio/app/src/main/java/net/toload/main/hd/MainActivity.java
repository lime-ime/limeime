/*
 *
 *  *
 *  **    Copyright 2015, The LimeIME Open Source Project
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

package net.toload.main.hd;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.MediaStore;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import net.toload.main.hd.data.Im;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.limedb.LimeDB;
import net.toload.main.hd.ui.HelpDialog;
import net.toload.main.hd.ui.ImportDialog;
import net.toload.main.hd.ui.ManageImFragment;
import net.toload.main.hd.ui.ManageRelatedFragment;
import net.toload.main.hd.ui.NewsDialog;
import net.toload.main.hd.ui.SetupImFragment;
import net.toload.main.hd.ui.ShareDbRunnable;
import net.toload.main.hd.ui.ShareRelatedDbRunnable;
import net.toload.main.hd.ui.ShareRelatedTxtRunnable;
import net.toload.main.hd.ui.ShareTxtRunnable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;
    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;
    //private CharSequence mCode;

    private LimeDB datasource;
    private List<Im> imlist;

    private ConnectivityManager connManager;
    private LIMEPreferenceManager mLIMEPref;

    private ProgressDialog progress;
    private MainActivityHandler handler;
    private Thread sharethread;

    //private Activity activity;

    //Admob
    InterstitialAd mInterstitialAd;
    Boolean intersitialAdShowed = true;

    IInAppBillingService mService;
    ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name,
                                       IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);

            boolean paymentFlag = mLIMEPref.getParameterBoolean(Lime.PAYMENT_FLAG, false);
            if (!paymentFlag) {
                try {
                    Bundle ownedItems = mService.getPurchases(3, getPackageName(), "inapp", null);
                    int response = ownedItems.getInt("RESPONSE_CODE");
                    if (response == 0) {
                        ArrayList<String> owned = ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
                        ArrayList<String> purchaseDataList = ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
                        ArrayList<String> signatureList = ownedItems.getStringArrayList("INAPP_DATA_SIGNATURE_LIST");
                        //String continuationToken = ownedItems.getString("INAPP_CONTINUATION_TOKEN");

                        assert purchaseDataList != null;
                        for (int i = 0; i < purchaseDataList.size(); ++i) {
                            String purchaseData = purchaseDataList.get(i);
                            assert signatureList != null;
                            String signature = signatureList.get(i);
                            assert owned != null;
                            String sku = owned.get(i);
                            mLIMEPref.setParameter(Lime.PAYMENT_FLAG, true);
                            mLIMEPref.setParameter("purchanseData", purchaseData);
                            mLIMEPref.setParameter("signature", signature);
                            mLIMEPref.setParameter("sku", sku);
                        }

                    }

                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    };
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

   /* @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

         if (requestCode == 1001) {
             String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
            //int responseCode = data.getIntExtra("RESPONSE_CODE", 0);
            //String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");

            if (resultCode == RESULT_OK) {
                mLIMEPref.setParameter(Lime.PAYMENT_FLAG, true);
                showToastMessage(getResources().getString(R.string.payment_service_success), Toast.LENGTH_LONG);
                //Log.i("LIME", "purchasing complete " + new Date() + " / " + purchaseData);
            }
         }

    }*/

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mService != null) {
            unbindService(mServiceConn);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        SetupImFragment ImFragment = (SetupImFragment) getSupportFragmentManager().findFragmentByTag("SetupImFragment");
        if (ImFragment == null) return;
        if (hasFocus && ImFragment.isVisible()) ImFragment.initialbutton();

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getString(R.string.global_exit_title));
            builder.setCancelable(false);
            builder.setPositiveButton(getResources().getString(R.string.dialog_confirm),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // Kill and stop my activity
                            //android.os.Process.killProcess(android.os.Process.myPid());
                            System.exit(0);
                        }
                    });
            builder.setNegativeButton(getResources().getString(R.string.dialog_cancel),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });

            AlertDialog alert = builder.create();
            alert.show();

            /*SetupImFragment ImFragment  = (SetupImFragment) getSupportFragmentManager().findFragmentByTag("SetupImFragment");
            if(ImFragment == null || !ImFragment.isVisible())  onNavigationDrawerItemSelected(0);
            else finish();
            return true;*/
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://net.toload.main.hd/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.disconnect();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);


        handler = new MainActivityHandler(this);

        progress = new ProgressDialog(this);
        progress.setMax(100);
        progress.setCancelable(false);

        connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        this.mLIMEPref = new LIMEPreferenceManager(this);

        LIME.PACKAGE_NAME = getApplicationContext().getPackageName();

        // initial imlist
        initialImList();

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        //mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));

        boolean paymentflag = mLIMEPref.getParameterBoolean(Lime.PAYMENT_FLAG, false);
        if (!paymentflag) {
            purchaseVerification();
        }

        // Handle Import Text from other application
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = getIntent().getType();
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                handleSendText(getIntent());
            }
        } else if (Intent.ACTION_VIEW.equals(action) && type != null) {
            String scheme = intent.getScheme();
            ContentResolver resolver = getContentResolver();

            if (ContentResolver.SCHEME_CONTENT.equals(scheme)
                    || ContentResolver.SCHEME_FILE.equals(scheme)
                    || scheme.equals("http") || scheme.equals("https") || scheme.equals("ftp")) {
                Uri uri = intent.getData();
                String fileName = getContentName(resolver, uri);
                if (fileName == null) {
                    fileName = uri.getLastPathSegment();
                }
                InputStream input = null;
                try {
                    input = resolver.openInputStream(uri);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                String importFilepath = Lime.DATABASE_FOLDER_EXTERNAL + fileName;
                InputStreamToFile(input, importFilepath);
                showToastMessage("Got file " + importFilepath, Toast.LENGTH_SHORT);
            }

        }

        String versionstr = "";
        PackageInfo pInfo;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionstr = "v" + pInfo.versionName + " - " + pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        String cversion = mLIMEPref.getParameterString("current_version", "");
        if (cversion == null || cversion.isEmpty() || !cversion.equals(versionstr)) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            HelpDialog dialog = HelpDialog.newInstance();
            dialog.show(ft, "helpdialog");
            mLIMEPref.setParameter("current_version", versionstr);
        }

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    private String getContentName(ContentResolver resolver, Uri uri) {
        Cursor cursor = resolver.query(uri, null, null, null, null);
        if (cursor == null) return null;
        cursor.moveToFirst();
        int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
        if (nameIndex >= 0) {
            return cursor.getString(nameIndex);
        } else {
            cursor.close();
            return null;
        }
    }

    private void InputStreamToFile(InputStream in, String file) {
        try {
            OutputStream out = new FileOutputStream(new File(file));

            int size;
            byte[] buffer = new byte[102400];

            while ((size = in.read(buffer)) != -1) {
                out.write(buffer, 0, size);
            }

            out.close();
        } catch (Exception e) {
            Log.e("MainActivity", "InputStreamToFile exception: " + e.getMessage());
        }
    }

    void handleSendText(Intent intent) {
        String importtext = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (importtext != null && !importtext.isEmpty()) {
            android.app.FragmentTransaction ft = getFragmentManager().beginTransaction();
            ImportDialog dialog = ImportDialog.newInstance(importtext);
            dialog.show(ft, "importdialog");
        }
    }

    public void purchaseVerification() {
        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        mService = null;
        bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);

        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                .build();

        //Admob IntersitialAD
        //Only show intersitialAd for one time.  It's quite annoying
        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId(LIME.publisher);

        mInterstitialAd.loadAd(adRequest);

        mInterstitialAd.setAdListener(new AdListener() {
            public void onAdLoaded() {
                if (intersitialAdShowed) return;
                if (mInterstitialAd.isLoaded()) {
                    mInterstitialAd.show();
                    intersitialAdShowed = true;
                    mInterstitialAd.setAdListener(null);
                    mInterstitialAd = null; //destroy mintersitialAd
                }
            }
        });
    }

    public void initialImList() {

        if (datasource == null)
            datasource = new LimeDB(this);

        imlist = new ArrayList<>();
        imlist = datasource.getIm(null, Lime.IM_TYPE_NAME);
       /* try {
            //datasource.open();
            //datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }*/
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments

        FragmentManager fragmentManager = getSupportFragmentManager();
        if (position == 0) {

            fragmentManager.beginTransaction()
                    .replace(R.id.container, SetupImFragment.newInstance(position), "SetupImFragment")
                    .addToBackStack("SetupImFragment")
                    .commit();
        } else if (position == 1) {
            fragmentManager.beginTransaction()
                    .replace(R.id.container, ManageRelatedFragment.newInstance(position), "ManageRelatedFragment")
                    .addToBackStack("ManageRelatedFragment")
                    .commit();
        } else {

            initialImList();
            int number = position - 2;
            String table = imlist.get(number).getCode();
            fragmentManager.beginTransaction()
                    .replace(R.id.container, ManageImFragment.newInstance(position, table), "ManageImFragment_" + table)
                    .addToBackStack("ManageImFragment_" + table)
                    .commit();
        }
    }

    public void onSectionAttached(int number) {
        if (number == 0) {
            mTitle = this.getResources().getString(R.string.default_menu_initial);
            //mCode = "initial";
        } else if (number == 1) {
            mTitle = this.getResources().getString(R.string.default_menu_related);
            //mCode = "related";
        } else {
            int position = number - 2;
            mTitle = imlist.get(position).getDesc();
            //mCode = imlist.get(position).getCode();
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        //actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD); //setNavigationMode is deprecated after API21 (v5.0).
        if (actionBar == null) throw new AssertionError();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        //int id = item.getItemId();

        //noinspection SimplifiableIfStatement
       /* if (id == R.id.action_settings) {
            return true;
        }*/

        return super.onOptionsItemSelected(item);
    }

    public void purchase(String productid) {

        if (connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()) {

            if (mService != null) {

                Bundle buyIntentBundle;
                try {
                    buyIntentBundle = mService.getBuyIntent(3, getPackageName(),
                            productid, "inapp", "callback/" + productid);

                    int status = buyIntentBundle.getInt("RESPONSE_CODE");

                    if (status == 0) {
                        PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
                        try {
                            assert pendingIntent != null;
                            startIntentSenderForResult(pendingIntent.getIntentSender(),
                                    Lime.PAYMENT_REQUEST_CODE, new Intent(), 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            e.printStackTrace();
                        }
                    } else {
                        showToastMessage(getResources().getString(R.string.payment_service_failed), Toast.LENGTH_LONG);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else {
                showToastMessage(getResources().getString(R.string.payment_service_failed), Toast.LENGTH_LONG);
            }
        } else {
            showToastMessage(getResources().getString(R.string.error_network_failed), Toast.LENGTH_LONG);
        }
    }

    public void showToastMessage(String msg, int length) {
        Toast toast = Toast.makeText(this, msg, length);
        toast.show();
    }

    public void initialShare(String imtype) {
        sharethread = new Thread(new ShareTxtRunnable(this, imtype, handler));
        sharethread.start();
    }

    public void initialShareDb(String imtype) {
        sharethread = new Thread(new ShareDbRunnable(this, imtype, handler));
        sharethread.start();
    }

    public void initialShareRelated() {
        sharethread = new Thread(new ShareRelatedTxtRunnable(this, handler));
        sharethread.start();
    }

    public void initialShareRelatedDb() {
        sharethread = new Thread(new ShareRelatedDbRunnable(this, handler));
        sharethread.start();
    }

    public void showProgress() {
        if (!progress.isShowing()) {
            progress.show();
        }
    }

    public void cancelProgress() {
        if (progress.isShowing()) {
            progress.dismiss();
        }
    }

    public void updateProgress(int value) {
        if (!progress.isShowing()) {
            progress.setProgress(value);
        }
    }

    public void updateProgress(String value) {
        if (progress.isShowing()) {
            progress.setMessage(value);
        }
    }

    public void shareTo(String filepath, String type) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType(type);

        File target = new File(filepath);
        Uri targetfile = Uri.fromFile(target);
        sharingIntent.putExtra(Intent.EXTRA_STREAM, targetfile);

        sharingIntent.putExtra(Intent.EXTRA_TEXT, target.getName());
        startActivity(Intent.createChooser(sharingIntent, target.getName()));
    }

    public void initialDefaultPreference() {

        /*String keyboard_state = mLIMEPref.getParameterString("keyboard_state");
        if(keyboard_state.isEmpty()){
            mLIMEPref.setParameter("keyboard_state", "0;1;2;3;4;5;6;7;8;9;10;11");
        }

        Boolean persistent_language_mode = mLIMEPref.getParameterBoolean("persistent_language_mode", false);
        mLIMEPref.setParameter("persistent_language_mode", persistent_language_mode);

        Boolean number_row_in_english = mLIMEPref.getParameterBoolean("number_row_in_english", true);
        mLIMEPref.setParameter("number_row_in_english", number_row_in_english);

        Boolean hide_software_keyboard_typing_with_physical = mLIMEPref.getParameterBoolean("hide_software_keyboard_typing_with_physical", true);
        mLIMEPref.setParameter("hide_software_keyboard_typing_with_physical", hide_software_keyboard_typing_with_physical);

        Boolean show_arrow_key = mLIMEPref.getParameterBoolean("show_arrow_key", false);
        mLIMEPref.setParameter("hide_software_keyboard_typing_with_physical", show_arrow_key);

        String split_keyboard_mode = mLIMEPref.getParameterString("split_keyboard_mode", "0");
        mLIMEPref.setParameter("split_keyboard_mode", split_keyboard_mode);

        Boolean fixed_candidate_view_display = mLIMEPref.getParameterBoolean("fixed_candidate_view_display", true);
        mLIMEPref.setParameter("fixed_candidate_view_display", fixed_candidate_view_display);

        String keyboard_size = mLIMEPref.getParameterString("keyboard_size", "1");
        mLIMEPref.setParameter("keyboard_size", keyboard_size);

        String font_size = mLIMEPref.getParameterString("font_size", "1");
        mLIMEPref.setParameter("font_size", font_size);

        Boolean vibrate_on_keypress = mLIMEPref.getParameterBoolean("vibrate_on_keypress", false);
        mLIMEPref.setParameter("vibrate_on_keypress", vibrate_on_keypress);

        String vibrate_level = mLIMEPref.getParameterString("vibrate_level", "40");
        mLIMEPref.setParameter("vibrate_level", vibrate_level);

        Boolean sound_on_keypress = mLIMEPref.getParameterBoolean("sound_on_keypress", false);
        mLIMEPref.setParameter("sound_on_keypress", sound_on_keypress);

        Boolean auto_chinese_symbol = mLIMEPref.getParameterBoolean("auto_chinese_symbol", false);
        mLIMEPref.setParameter("auto_chinese_symbol", auto_chinese_symbol);

        Boolean disable_physical_selkey = mLIMEPref.getParameterBoolean("disable_physical_selkey", false);
        mLIMEPref.setParameter("disable_physical_selkey", disable_physical_selkey);

        String auto_commit = mLIMEPref.getParameterString("auto_commit", "0");
        mLIMEPref.setParameter("auto_commit", auto_commit);

        String selkey_option = mLIMEPref.getParameterString("selkey_option", "0");
        mLIMEPref.setParameter("selkey_option", selkey_option);

        String phonetic_keyboard_type = mLIMEPref.getParameterString("phonetic_keyboard_type", "standard");
        mLIMEPref.setParameter("phonetic_keyboard_type", phonetic_keyboard_type);

        String physical_keyboard_type = mLIMEPref.getParameterString("physical_keyboard_type", "normal_keyboard");
        mLIMEPref.setParameter("physical_keyboard_type", physical_keyboard_type);

        String han_convert_option = mLIMEPref.getParameterString("han_convert_option", "0");
        mLIMEPref.setParameter("han_convert_option", han_convert_option);

        String custom_im_reverselookup = mLIMEPref.getParameterString("custom_im_reverselookup", "none");
        mLIMEPref.setParameter("custom_im_reverselookup", custom_im_reverselookup);

        String cj_im_reverselookup = mLIMEPref.getParameterString("cj_im_reverselookup", "none");
        mLIMEPref.setParameter("cj_im_reverselookup", cj_im_reverselookup);

        String scj_im_reverselookup = mLIMEPref.getParameterString("scj_im_reverselookup", "none");
        mLIMEPref.setParameter("scj_im_reverselookup", scj_im_reverselookup);

        String cj5_im_reverselookup = mLIMEPref.getParameterString("cj5_im_reverselookup", "none");
        mLIMEPref.setParameter("cj5_im_reverselookup", cj5_im_reverselookup);

        String ecj_im_reverselookup = mLIMEPref.getParameterString("ecj_im_reverselookup", "none");
        mLIMEPref.setParameter("ecj_im_reverselookup", ecj_im_reverselookup);

        String dayi_im_reverselookup = mLIMEPref.getParameterString("dayi_im_reverselookup", "none");
        mLIMEPref.setParameter("dayi_im_reverselookup", dayi_im_reverselookup);

        String bpmf_im_reverselookup = mLIMEPref.getParameterString("bpmf_im_reverselookup", "none");
        mLIMEPref.setParameter("bpmf_im_reverselookup", bpmf_im_reverselookup);

        String ez_im_reverselookup = mLIMEPref.getParameterString("ez_im_reverselookup", "none");
        mLIMEPref.setParameter("ez_im_reverselookup", ez_im_reverselookup);

        String array_im_reverselookup = mLIMEPref.getParameterString("array_im_reverselookup", "none");
        mLIMEPref.setParameter("array_im_reverselookup", array_im_reverselookup);

        String array10_im_reverselookup = mLIMEPref.getParameterString("array10_im_reverselookup", "none");
        mLIMEPref.setParameter("array10_im_reverselookup", array10_im_reverselookup);

        String wb_im_reverselookup = mLIMEPref.getParameterString("wb_im_reverselookup", "none");
        mLIMEPref.setParameter("wb_im_reverselookup", wb_im_reverselookup);

        String pinyin_im_reverselookup = mLIMEPref.getParameterString("pinyin_im_reverselookup", "none");
        mLIMEPref.setParameter("pinyin_im_reverselookup", pinyin_im_reverselookup);

        String similiar_list = mLIMEPref.getParameterString("similiar_list", "20");
        mLIMEPref.setParameter("similiar_list", similiar_list);

        Boolean similiar_enable = mLIMEPref.getParameterBoolean("similiar_enable", true);
        mLIMEPref.setParameter("similiar_enable", similiar_enable);

        Boolean english_dictionary_enable = mLIMEPref.getParameterBoolean("english_dictionary_enable", true);
        mLIMEPref.setParameter("english_dictionary_enable", english_dictionary_enable);

        Boolean english_dictionary_physical_keyboard = mLIMEPref.getParameterBoolean("english_dictionary_physical_keyboard", false);
        mLIMEPref.setParameter("similiar_enable", english_dictionary_physical_keyboard);

        Boolean candidate_switch = mLIMEPref.getParameterBoolean("candidate_switch", true);
        mLIMEPref.setParameter("candidate_switch", candidate_switch);

        Boolean candidate_suggestion = mLIMEPref.getParameterBoolean("candidate_suggestion", true);
        mLIMEPref.setParameter("candidate_suggestion", candidate_suggestion);

        Boolean learn_phrase = mLIMEPref.getParameterBoolean("learn_phrase", true);
        mLIMEPref.setParameter("learn_phrase", learn_phrase);

        Boolean learning_switch = mLIMEPref.getParameterBoolean("learning_switch", true);
        mLIMEPref.setParameter("learning_switch", learning_switch);

        Boolean physical_keyboard_sort = mLIMEPref.getParameterBoolean("physical_keyboard_sort", true);
        mLIMEPref.setParameter("physical_keyboard_sort", physical_keyboard_sort);

        Boolean accept_number_index = mLIMEPref.getParameterBoolean("accept_number_index", false);
        mLIMEPref.setParameter("accept_number_index", accept_number_index);

        Boolean accept_symbol_index = mLIMEPref.getParameterBoolean("accept_symbol_index", false);
        mLIMEPref.setParameter("accept_symbol_index", accept_symbol_index);

        Boolean switch_english_mode = mLIMEPref.getParameterBoolean("switch_english_mode", false);
        mLIMEPref.setParameter("switch_english_mode", switch_english_mode);
*/

    }

    public void showMessageBoard() {
        try {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            NewsDialog dialog = NewsDialog.newInstance();
            dialog.show(ft, "newsdialog");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://net.toload.main.hd/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }
}
