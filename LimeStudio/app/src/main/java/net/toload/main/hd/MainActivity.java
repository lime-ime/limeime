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
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;



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

    final String TAG = "MainActivity";
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

    private AlertDialog progress;
    private MainActivityHandler handler;
    private Thread sharethread;

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
                    (dialog, id) -> {
                        // Kill and stop my activity
                        //android.os.Process.killProcess(android.os.Process.myPid());
                        System.exit(0);
                    });
            builder.setNegativeButton(getResources().getString(R.string.dialog_cancel),
                    (dialog, id) -> {
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
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Enable edge-to-edge display for API 35+ (Android 15+)
        // API 35 is required for edge-to-edge, but we enable it for all API levels for consistency
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_main);

        // Ensure ActionBar title is displayed
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
        }

        // Handle window insets for edge-to-edge display
        setupEdgeToEdge();


        handler = new MainActivityHandler(this);

        //ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        LIMEPreferenceManager mLIMEPref = new LIMEPreferenceManager(this);

        LIME.PACKAGE_NAME = getApplicationContext().getPackageName();

        // initial imlist
        initialImList();

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        //mTitle = getTitle();

        // Set up the drawer.
        assert mNavigationDrawerFragment != null;
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                findViewById(R.id.drawer_layout));

        

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

                File importDir = new File(getCacheDir(), "imports");
                if(!importDir.mkdirs()) Log.w(TAG,"Failed to create import dir");
                File importFile = new File(importDir, fileName);
                String importFilepath = importFile.getAbsolutePath();
                InputStreamToFile(input, importFilepath);
                showToastMessage("Got file " + importFilepath, Toast.LENGTH_SHORT);
            }

        }

        String versionstr = "";
        PackageInfo pInfo;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            long versionCode = PackageInfoCompat.getLongVersionCode(pInfo);
            versionstr = "v" + pInfo.versionName + " - " + versionCode;
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
            OutputStream out = new FileOutputStream(file);

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
            androidx.fragment.app.FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ImportDialog dialog = ImportDialog.newInstance(importtext);
            dialog.show(ft, "importdialog");
        }
    }

    public void initialImList() {

        if (datasource == null)
            datasource = new LimeDB(this);

        imlist = new ArrayList<>();
        imlist = datasource.getIm(null, Lime.IM_TYPE_NAME);
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
        return super.onOptionsItemSelected(item);
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
        if (progress == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(false);
            View view = LayoutInflater.from(this).inflate(R.layout.progress, null);
            builder.setView(view);
            progress = builder.create();
        }
        if (!progress.isShowing()) {
            progress.show();
        }
    }

    public void cancelProgress() {
        if (progress != null && progress.isShowing()) {
            progress.dismiss();
        }
        progress = null;
    }

    public void updateProgress(int value) {
        if (progress != null && progress.isShowing()) {
            ProgressBar pb = progress.findViewById(R.id.progress_bar);
            if(pb != null){
                pb.setProgress(value);
            }
        }
    }

    public void updateProgress(String value) {
        if (progress != null && progress.isShowing()) {
            TextView tv = progress.findViewById(R.id.progress_text);
            if(tv != null){
                tv.setText(value);
            }
        }
    }

    public void shareTo(String filepath, String type) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType(type);

        File target = new File(filepath);
        
        Uri targetfile = androidx.core.content.FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".fileprovider", target);
        sharingIntent.putExtra(Intent.EXTRA_STREAM, targetfile);
        sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        sharingIntent.putExtra(Intent.EXTRA_TEXT, target.getName());
        startActivity(Intent.createChooser(sharingIntent, target.getName()));
    }

    public void initialDefaultPreference() {


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

    /**
     * Setup edge-to-edge display with proper window insets handling.
     * This ensures UI elements are not obscured by system bars on API 35+.
     */
    @SuppressWarnings("deprecation")
    private void setupEdgeToEdge() {
        // Apply window insets to the main content container (FrameLayout)
        // ActionBar already handles its own space, so we only need to account for status bar
        View container = findViewById(R.id.container);
        if (container != null) {
            ViewCompat.setOnApplyWindowInsetsListener(container, (v, insets) -> {
                int systemBarsType = WindowInsetsCompat.Type.systemBars();
                int topInset = insets.getInsets(systemBarsType).top;
                int bottomInset = insets.getInsets(systemBarsType).bottom;
                int leftInset = insets.getInsets(systemBarsType).left;
                int rightInset = insets.getInsets(systemBarsType).right;
                
                // Apply padding: top = status bar only (ActionBar handles its own space),
                // left/right/bottom = system bars
                v.setPadding(leftInset, topInset, rightInset, bottomInset);

                return insets;
            });
        }
        
        // DrawerLayout extends to edges - no padding needed on DrawerLayout itself
        // The drawer fragment's ListView will handle its own content insets if needed

        // Set status bar and navigation bar to transparent for edge-to-edge effect
        // This works on all API levels, but is required for API 35+
        // Note: setStatusBarColor and setNavigationBarColor are deprecated in API 35+,
        // but we use them with suppression for backward compatibility
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            @SuppressWarnings("deprecation")
            android.view.Window window = getWindow();
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        }
        
        // Set status bar icon appearance to dark (black icons) for better visibility
        // Since status bar is transparent and content behind may be light, use dark icons
        View decorView = getWindow().getDecorView();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            // API 23+ (Marshmallow+): Use WindowInsetsControllerCompat
            WindowInsetsControllerCompat windowInsetsController = ViewCompat.getWindowInsetsController(decorView);
            if (windowInsetsController != null) {
                // Use dark status bar icons (black) for visibility on light backgrounds
                // setAppearanceLightStatusBars(true) = light status bar appearance = dark icons
                windowInsetsController.setAppearanceLightStatusBars(true);
                // Use dark navigation bar icons for consistency
                windowInsetsController.setAppearanceLightNavigationBars(true);
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // API 21-22: SYSTEM_UI_FLAG_LIGHT_STATUS_BAR is not available (introduced in API 23)
            // On API 21-22, we cannot change icon color programmatically
            // Set a dark status bar so white icons are visible (compromise for API 21-22)
            @SuppressWarnings("deprecation")
            android.view.Window window = getWindow();
            // Use a dark color so white icons are visible
            // This maintains some edge-to-edge while ensuring icons are visible
            window.setStatusBarColor(0xFF000000); // Solid black
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }
}
