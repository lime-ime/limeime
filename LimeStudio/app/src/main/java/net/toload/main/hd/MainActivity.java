package net.toload.main.hd;


import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;

import net.toload.main.hd.data.Im;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.limedb.LimeDB;
import net.toload.main.hd.ui.ManageRelatedFragment;
import net.toload.main.hd.ui.SetupImFragment;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;
    private SearchServer SearchSrv = null;


    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;
    //private CharSequence mCode;

    private LimeDB datasource;
    private List<Im> imlist;

    private ConnectivityManager connManager;
    private LIMEPreferenceManager mLIMEPref;

    //Admob
    InterstitialAd mInterstitialAd;
    Boolean intersitialAdShowed=false;

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

            boolean paymentflag = mLIMEPref.getParameterBoolean(Lime.PAYMENT_FLAG, false);
            if(!paymentflag) {
                try {
                    Bundle ownedItems = mService.getPurchases(3, getPackageName(), "inapp", null);
                    int response = ownedItems.getInt("RESPONSE_CODE");
                    if (response == 0) {
                        ArrayList<String> owned =  ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
                        ArrayList<String>  purchaseDataList =  ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
                        ArrayList<String>  signatureList =  ownedItems.getStringArrayList("INAPP_DATA_SIGNATURE_LIST");
                        //String continuationToken = ownedItems.getString("INAPP_CONTINUATION_TOKEN");

                        for (int i = 0; i < purchaseDataList.size(); ++i) {
                            String purchaseData = purchaseDataList.get(i);
                            String signature = signatureList.get(i);
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
    public void  onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        SetupImFragment ImFragment  = (SetupImFragment) getSupportFragmentManager().findFragmentByTag("SetupImFragment");
        if(ImFragment == null) return;
        if( hasFocus && ImFragment.isVisible()) ImFragment.initialbutton();

    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            SetupImFragment ImFragment  = (SetupImFragment) getSupportFragmentManager().findFragmentByTag("SetupImFragment");
            if(ImFragment == null || !ImFragment.isVisible())  onNavigationDrawerItemSelected(0);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        this.SearchSrv.initialCache();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        this.SearchSrv = new SearchServer(this);
        this.mLIMEPref = new LIMEPreferenceManager(this);



        // initial imlist
        initialImList();

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));

        boolean paymentflag = mLIMEPref.getParameterBoolean(Lime.PAYMENT_FLAG, false);
        if(!paymentflag) {
            Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
            serviceIntent.setPackage("com.android.vending");
            mService =null;
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

    }

    public void initialImList(){

        if(datasource == null)
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
        }else if (position == 1){
            fragmentManager.beginTransaction()
                    .replace(R.id.container, ManageRelatedFragment.newInstance(position), "ManageRelatedFragment")
                    .addToBackStack("ManageRelatedFragment")
                    .commit();
        }else{
            initialImList();
            int number = position - 2;
            String code = imlist.get(number).getCode();
            fragmentManager.beginTransaction()
                    .replace(R.id.container, net.toload.main.hd.ui.ManageImFragment.newInstance(position, code), "ManageImFragment_" + code)
                    .addToBackStack("ManageImFragment_" + code)
                    .commit();
        }
    }

    public void onSectionAttached(int number) {
        if (number == 0) {
            mTitle = this.getResources().getString(R.string.default_menu_initial);
            //mCode = "initial";
        } else if (number == 1){
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

    public void purchase(String productid){

        if (connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()) {

            if(mService != null){

                Bundle buyIntentBundle;
                try {
                    buyIntentBundle = mService.getBuyIntent(3, getPackageName(),
                            productid, "inapp", "callback/"+productid);

                    int status = buyIntentBundle.getInt("RESPONSE_CODE");

                    if(status == 0){
                        PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
                        try {
                            startIntentSenderForResult(pendingIntent.getIntentSender(),
                                    1001, new Intent(), 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            e.printStackTrace();
                        }
                    }else{
                        showToastMessage(getResources().getString(R.string.payment_service_failed), Toast.LENGTH_LONG);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }else{
                showToastMessage(getResources().getString(R.string.payment_service_failed), Toast.LENGTH_LONG);
            }
        }else{
            showToastMessage(getResources().getString(R.string.error_network_failed), Toast.LENGTH_LONG);
        }
    }

    public void showToastMessage(String msg, int length) {
        Toast toast = Toast.makeText(this, msg, length);
        toast.show();
    }


}
