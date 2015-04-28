package net.toload.main.hd.ui;

import android.app.Activity;

import net.toload.main.hd.data.DataSource;
import net.toload.main.hd.data.Related;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class ManageRelatedRunnable implements Runnable{


    private ManageRelatedHandler handler;
    private Activity activity;
    private DataSource datasource;
    private String query;

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    public ManageRelatedRunnable(ManageRelatedHandler handler, Activity activity, String query) {
        this.handler = handler;
        this.activity = activity;
        this.query = query;

        datasource = new DataSource(this.activity);
    }

    public void run() {
        handler.showProgress();
        handler.updateGridView(loadRelated(query));
    }

    private List<Related> loadRelated(String pword){
        List<Related> results = new ArrayList<>();

        try {
            datasource.open();
            results = datasource.loadRelated(pword);
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

}
