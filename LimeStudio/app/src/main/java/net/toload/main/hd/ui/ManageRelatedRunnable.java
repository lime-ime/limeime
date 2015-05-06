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
    private int maximum;
    private int offset;

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    public ManageRelatedRunnable(ManageRelatedHandler handler, Activity activity, String query, int maximum, int offset) {
        this.handler = handler;
        this.activity = activity;
        this.query = query;
        this.maximum = maximum;
        this.offset = offset;

        datasource = new DataSource(this.activity);
    }

    public void run() {
        handler.showProgress();

        handler.updateGridView(loadRelated(query, maximum, offset));

        /*if(maximum > 0){
            handler.updateGridViewInitial(loadRelated(query, maximum));
        }else{
            handler.updateGridView(loadRelated(query, maximum));
        }*/
    }

    private List<Related> loadRelated(String pword, int maximum, int offset){
        List<Related> results = new ArrayList<>();

        try {
            datasource.open();
            results = datasource.loadRelated(pword, maximum, offset);
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

}
