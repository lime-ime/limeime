package net.toload.main.hd;

import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import net.toload.main.hd.data.DataSource;
import net.toload.main.hd.data.Im;
import net.toload.main.hd.ui.ManageRelatedFragment;
import net.toload.main.hd.ui.SetupImFragment;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;
    private CharSequence mCode;

    private DataSource datasource;
    private List<Im> imlist;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // initial imlist
        initialImList();

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));
    }

    public void initialImList(){

        if(datasource == null)
            datasource = new DataSource(this);

        imlist = new ArrayList<Im>();
        try {
            datasource.open();
            imlist = datasource.getIm(null, Lime.IM_TYPE_NAME);
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments

        FragmentManager fragmentManager = getSupportFragmentManager();
        if (position == 0) {
            fragmentManager.beginTransaction()
                    .replace(R.id.container, SetupImFragment.newInstance(position))
                    .commit();
        }else if (position == 1){
            fragmentManager.beginTransaction()
                    .replace(R.id.container, ManageRelatedFragment.newInstance(position))
                    .commit();
        }else{
            initialImList();
            int number = position - 2;
            String code = imlist.get(number).getCode();
            fragmentManager.beginTransaction()
                    .replace(R.id.container, net.toload.main.hd.ui.ManageImFragment.newInstance(position, code))
                    .commit();
        }
    }

    public void onSectionAttached(int number) {
        if (number == 0) {
            mTitle = this.getResources().getString(R.string.default_menu_initial);
            mCode = "initial";
        } else if (number == 1){
            mTitle = this.getResources().getString(R.string.default_menu_related);
            mCode = "related";
        } else {
            int position = number - 2;
            mTitle = imlist.get(position).getDesc();
            mCode = imlist.get(position).getCode();
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
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
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
       /* if (id == R.id.action_settings) {
            return true;
        }*/

        return super.onOptionsItemSelected(item);
    }

}
