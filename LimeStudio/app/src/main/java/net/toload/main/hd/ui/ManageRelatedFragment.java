package net.toload.main.hd.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import net.toload.main.hd.Lime;
import net.toload.main.hd.MainActivity;
import net.toload.main.hd.R;
import net.toload.main.hd.data.DataSource;
import net.toload.main.hd.data.Related;

import java.sql.SQLException;
import java.util.List;

/**
 * Fragment used for managing interactions for and presentation of a navigation drawer.
 * See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented here.
 */

/**
 * A placeholder fragment containing a simple view.
 */
public class ManageRelatedFragment extends Fragment {

    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";

    private GridView gridManageRelated;

    private Button btnManageRelatedAdd;
    private Button btnManageRelatedSearch;
    private Button btnManageRelatedPrevious;
    private Button btnManageRelatedNext;

    private EditText edtManageRelatedSearch;
    private TextView txtNavigationInfo;

    private List<Related> relatedlist;

    private int page = 0;
    private int total = 0;
    private boolean searchreset = false;

    private String prequery = "";

    private Activity activity;
    private ManageRelatedHandler handler;
    private ManageRelatedAdapter adapter;

    private Thread ManageRelatedthread;

    private DataSource datasource;

    private ProgressDialog progress;

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static ManageRelatedFragment newInstance(int sectionNumber) {
        ManageRelatedFragment fragment = new ManageRelatedFragment();
        Bundle args = new Bundle();
                args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    public ManageRelatedFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_manage_related, container, false);

        this.activity = this.getActivity();
        this.datasource = new DataSource(this.activity);
        this.handler = new ManageRelatedHandler(this);

        this.progress = new ProgressDialog(this.activity);
        this.progress.setCancelable(false);
        this.progress.setMessage(getResources().getString(R.string.manage_related_loading));

        this.gridManageRelated = (GridView) root.findViewById(R.id.gridManageRelated);
        this.gridManageRelated.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    datasource.open();
                    Related w = datasource.getRelated(id);
                    datasource.close();
                    FragmentTransaction ft = getFragmentManager().beginTransaction();

                    // Create and show the dialog.
                    ManageRelatedEditDialog dialog = ManageRelatedEditDialog.newInstance();
                    dialog.setHandler(handler, w);
                    dialog.show(ft, "editdialog");
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });

        this.btnManageRelatedAdd = (Button) root.findViewById(R.id.btnManageRelatedAdd);
        this.btnManageRelatedAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ManageRelatedAddDialog dialog = ManageRelatedAddDialog.newInstance();
                dialog.setHandler(handler);
                dialog.show(ft, "adddialog");
            }
        });

        this.btnManageRelatedNext = (Button) root.findViewById(R.id.btnManageRelatedNext);
        this.btnManageRelatedNext.setEnabled(false);
        this.btnManageRelatedNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int checkrecord = Lime.IM_MANAGE_DISPLAY_AMOUNT * (page + 1);
                if (checkrecord < total) {
                    page++;
                }
                searchrelated();
                //updateGridView(relatedlist);
            }
        });
        this.btnManageRelatedPrevious = (Button) root.findViewById(R.id.btnManageRelatedPrevious);
        this.btnManageRelatedPrevious.setEnabled(false);
        this.btnManageRelatedPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (page > 0) {
                    page--;
                }
                searchrelated();
                //updateGridView(relatedlist);
            }
        });

        this.edtManageRelatedSearch = (EditText) root.findViewById(R.id.edtManageRelatedSearch);
        this.edtManageRelatedSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchreset = false;
                btnManageRelatedSearch.setText(getResources().getText(R.string.manage_related_search));
            }
        });

        this.btnManageRelatedSearch = (Button) root.findViewById(R.id.btnManageRelatedSearch);
        this.btnManageRelatedSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!searchreset) {
                    String query = edtManageRelatedSearch.getText().toString();
                    if (query != null && query.length() > 0 &&
                            ( prequery == null || !prequery.equals(query) || !searchreset) ) {
                        query = query.trim();
                        searchrelated(query);
                    }
                    searchreset = true;
                    btnManageRelatedSearch.setText(getResources().getText(R.string.manage_related_reset));
                } else {
                    total = 0;
                    searchrelated(null);
                    edtManageRelatedSearch.setText("");
                    searchreset = false;
                    btnManageRelatedSearch.setText(getResources().getText(R.string.manage_related_search));
                }
            }
        });

        this.txtNavigationInfo = (TextView) root.findViewById(R.id.txtNavigationInfo);

        searchrelated(null);

        return root;
    }

    public void searchrelated(){
        searchrelated(prequery);
    }

    public void searchrelated(String curquery){

        int offset = Lime.IM_MANAGE_DISPLAY_AMOUNT * page;

        if((curquery == null && total == 0) || curquery != prequery ){
            try {
                datasource.open();
                total = datasource.getRelatedSize(curquery);
                page = 0;
                datasource.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        if(ManageRelatedthread != null && ManageRelatedthread.isAlive()){
            handler.removeCallbacks(ManageRelatedthread);
        }
        ManageRelatedthread = new Thread(new ManageRelatedRunnable(handler, activity, curquery,
                                                                Lime.IM_MANAGE_DISPLAY_AMOUNT, offset));
        ManageRelatedthread.start();
        prequery = curquery;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        ((MainActivity) activity).onSectionAttached(
                getArguments().getInt(ARG_SECTION_NUMBER));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(this.ManageRelatedthread != null){
            this.handler.removeCallbacks(ManageRelatedthread);
        }
        if(this.progress.isShowing()){
            this.progress.cancel();
        }
        this.relatedlist = null;
    }

    public void showProgress(){
        if(!this.progress.isShowing()){
            this.progress.show();
        }
    }

    public void cancelProgress(){
        if(this.progress.isShowing()){
            this.progress.cancel();
        }
    }

    public void updateGridView(List<Related> relatedlist){

        this.relatedlist = relatedlist;

        int startrecord = Lime.IM_MANAGE_DISPLAY_AMOUNT * page;
        int endrecord = Lime.IM_MANAGE_DISPLAY_AMOUNT * (page + 1);

        if(page > 0){
            this.btnManageRelatedPrevious.setEnabled(true);
        }else{
            this.btnManageRelatedPrevious.setEnabled(false);
        }

        if(endrecord <= total){
            this.btnManageRelatedNext.setEnabled(true);
        }else{
            this.btnManageRelatedNext.setEnabled(false);
            endrecord = total;
        }

        if(total > 0){
            if(this.adapter == null){
                this.adapter = new ManageRelatedAdapter(this.activity, relatedlist);
                this.gridManageRelated.setAdapter(this.adapter);
            }else{
                this.adapter.setList(relatedlist);
                this.adapter.notifyDataSetChanged();
                this.gridManageRelated.setSelection(0);
            }
        }else{
            Toast.makeText(activity, R.string.no_search_result, Toast.LENGTH_SHORT).show();
        }

        String nav = "0";

        if(total > 0){
            nav = Lime.format(startrecord + 1) + "-" + Lime.format(endrecord);
            nav += " of " + Lime.format(total);
        }

        this.txtNavigationInfo.setText(nav);
        cancelProgress();

    }

    public void removeRelated(int id){

        // Remove from the temp list
        for(int i = 0 ; i < total ; i++){
           if(id== this.relatedlist.get(i).getId()){
               this.relatedlist.remove(i);
               break;
           }
        }

        // Remove from the database
        String removesql = "DELETE FROM " + Lime.DB_RELATED + " WHERE " + Lime.DB_COLUMN_ID + " = '" + id + "'";

        try {
            datasource.open();
            datasource.remove(removesql);
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        total--;
        searchrelated();
        //updateGridView(this.relatedlist);
    }

    public void addRelated(String pword, String cword, int score) {

        // Add to database
        Related obj = new Related();
             obj.setPword(pword);
             obj.setCword(cword);
             obj.setScore(score);

        String insertsql = Related.getInsertQuery(obj);

        try {
            datasource.open();
            datasource.insert(insertsql);
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        total++;
        searchrelated();

        // Add to temp list
        //page = 0;
        //this.relatedlist.add(0,obj);
        //updateGridView(this.relatedlist);
    }

    public void updateRelated(int id, String pword, String cword, int score) {

        // remove from temp list
        for(int i = 0 ; i < total ; i++){
            if(id== this.relatedlist.get(i).getId()){
                Related check = this.relatedlist.get(i);
                     check.setPword(pword);
                     check.setCword(cword);
                     check.setScore(score);
                this.relatedlist.remove(i);
                this.relatedlist.add(i, check);
                break;
            }
        }

        // Update record in the database
        String updatesql = "UPDATE " + Lime.DB_RELATED + " SET ";
                updatesql += Lime.DB_RELATED_COLUMN_PWORD + " = \"" + Lime.formatSqlValue(pword) + "\", ";
                updatesql += Lime.DB_RELATED_COLUMN_CWORD + " = \"" + Lime.formatSqlValue(cword) + "\", ";
                updatesql += Lime.DB_RELATED_COLUMN_SCORE + " = \"" + score + "\" ";
                updatesql += " WHERE " + Lime.DB_RELATED_COLUMN_ID + " = \"" + id + "\"";

        try {
            datasource.open();
            datasource.update(updatesql);
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        searchrelated();
        //updateGridView(this.relatedlist);
    }

}