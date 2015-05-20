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
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.vpadn.ads.VpadnAdRequest;
import com.vpadn.ads.VpadnAdSize;
import com.vpadn.ads.VpadnBanner;

import net.toload.main.hd.Lime;
import net.toload.main.hd.MainActivity;
import net.toload.main.hd.R;
import net.toload.main.hd.SearchServer;
import net.toload.main.hd.data.Im;
import net.toload.main.hd.data.Keyboard;
import net.toload.main.hd.data.Word;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.limedb.LimeDB;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment used for managing interactions for and presentation of a navigation drawer.
 * See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented here.
 */

/**
 * A placeholder fragment containing a simple view.
 */
public class ManageImFragment extends Fragment {

    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";
    private static final String ARG_SECTION_CODE = "section_code";

    private SearchServer SearchSrv = null;
    private GridView gridManageIm;

    private ToggleButton toggleManageIm;

    private Button btnManageImAdd;
    private Button btnManageImKeyboard;
    private Button btnManageImSearch;
    private Button btnManageImPrevious;
    private Button btnManageImNext;

    private EditText edtManageImSearch;
    private TextView txtNavigationInfo;

    private List<Im> imkeyboardlist;
    private List<Word> wordlist;
    private List<Keyboard> keyboardlist;

    private int page = 0;
    private int total = 0;
    private boolean searchroot = true;
    private boolean searchreset = false;

    private String prequery = "";

    private String code;
    private Activity activity;
    private ManageImHandler handler;
    private ManageImAdapter adapter;

    private Thread manageimthread;

    private LimeDB datasource;

    private ProgressDialog progress;
    private LIMEPreferenceManager mLIMEPref;

    // AD
    private RelativeLayout adBannerLayout;
    private VpadnBanner vpadnBanner = null;

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static ManageImFragment newInstance(int sectionNumber, String code) {
        ManageImFragment fragment = new ManageImFragment();
        Bundle args = new Bundle();
                args.putInt(ARG_SECTION_NUMBER, sectionNumber);
                args.putString(ARG_SECTION_CODE, code);
        fragment.setArguments(args);
        return fragment;
    }

    public ManageImFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_manage_im, container, false);

        this.activity = this.getActivity();
        this.datasource = new LimeDB(this.activity);
        this.SearchSrv = new SearchServer(this.activity);

        this.handler = new ManageImHandler(this);
        this.mLIMEPref = new LIMEPreferenceManager(activity);

        // initial imlist
        imkeyboardlist = new ArrayList<Im>();
        imkeyboardlist = datasource.getIm(null, Lime.IM_TYPE_KEYBOARD);
        /* try {
               datasource.open();
               datasource.close();
         } catch (SQLException e) {
              e.printStackTrace();
         }
*/
        this.progress = new ProgressDialog(this.activity);
        this.progress.setCancelable(false);
        this.progress.setMessage(getResources().getString(R.string.manage_im_loading));

        this.gridManageIm = (GridView) root.findViewById(R.id.gridManageIm);
        this.gridManageIm.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //try {
                    //datasource.open();
                    Word w = datasource.getWord(code, id);
                    //datasource.close();
                    FragmentTransaction ft = getFragmentManager().beginTransaction();

                    // Create and show the dialog.
                    ManageImEditDialog dialog = ManageImEditDialog.newInstance(code);
                    dialog.setHandler(handler, w);
                    dialog.show(ft, "editdialog");
                //} catch (SQLException e) {
                //    e.printStackTrace();
                //}
            }
        });

        this.btnManageImAdd = (Button) root.findViewById(R.id.btnManageImAdd);
        this.btnManageImAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ManageImAddDialog dialog = ManageImAddDialog.newInstance(code);
                                    dialog.setHandler(handler);
                dialog.show(ft, "adddialog");
            }
        });

        this.btnManageImKeyboard = (Button) root.findViewById(R.id.btnManageImKeyboard);
        this.btnManageImKeyboard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ManageImKeyboardDialog dialog = ManageImKeyboardDialog.newInstance();
                                       dialog.setHandler(handler, code);
                dialog.show(ft, "keyboarddialog");
            }
        });

        this.toggleManageIm = (ToggleButton) root.findViewById(R.id.toggleManageIm);
        this.toggleManageIm.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    searchroot = false;
                } else {
                    searchroot = true;
                }
                total = 0;
                prequery = "";
                edtManageImSearch.setText("");
                searchword(null);
                searchreset = false;
                btnManageImSearch.setText(getResources().getText(R.string.manage_im_search));
            }
        });

        this.btnManageImNext = (Button) root.findViewById(R.id.btnManageImNext);
        this.btnManageImNext.setEnabled(false);
        this.btnManageImNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int checkrecord = Lime.IM_MANAGE_DISPLAY_AMOUNT * (page + 1);
                if (checkrecord < total) {
                    page++;
                }
                searchword();
                //updateGridView(wordlist);
            }
        });
        this.btnManageImPrevious = (Button) root.findViewById(R.id.btnManageImPrevious);
        this.btnManageImPrevious.setEnabled(false);
        this.btnManageImPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (page > 0) {
                    page--;
                }
                searchword();
                //updateGridView(wordlist);
            }
        });

        this.edtManageImSearch = (EditText) root.findViewById(R.id.edtManageImSearch);
        this.edtManageImSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchreset = false;
                btnManageImSearch.setText(getResources().getText(R.string.manage_im_search));
            }
        });

        this.btnManageImSearch = (Button) root.findViewById(R.id.btnManageImSearch);
        this.btnManageImSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!searchreset) {
                    String query = edtManageImSearch.getText().toString();
                    if (query != null && query.length() > 0 &&
                            ( prequery == null || !prequery.equals(query) || !searchreset) ) {
                        query = query.trim();
                        searchword(query);
                    }
                    searchreset = true;
                    btnManageImSearch.setText(getResources().getText(R.string.manage_im_reset));
                } else {
                    total = 0;
                    searchword(null);
                    edtManageImSearch.setText("");
                    searchreset = false;
                    btnManageImSearch.setText(getResources().getText(R.string.manage_im_search));
                }
            }
        });

        this.txtNavigationInfo = (TextView) root.findViewById(R.id.txtNavigationInfo);

        // UpdateKeyboard display
        for(Im obj : imkeyboardlist){
            if(obj.getCode().equals(code)){
                btnManageImKeyboard.setText(obj.getDesc());
                break;
            }
        }

        searchword(null);

        // Handle AD Display
        boolean paymentflag = mLIMEPref.getParameterBoolean(Lime.PAYMENT_FLAG, false);
        if(!paymentflag) {
            adBannerLayout = (RelativeLayout) root.findViewById(R.id.adLayout);
            vpadnBanner = new VpadnBanner(getActivity(), Lime.VPON_BANNER_ID, VpadnAdSize.SMART_BANNER, "TW");

            VpadnAdRequest adRequest = new VpadnAdRequest();
            adRequest.setEnableAutoRefresh(true);
            vpadnBanner.loadAd(adRequest);

            adBannerLayout.addView(vpadnBanner);
        }

        return root;
    }

    public void searchword(){
        searchword(prequery);
    }

    public void searchword(String curquery){

        int offset = Lime.IM_MANAGE_DISPLAY_AMOUNT * page;

        if((curquery == null && total == 0) || curquery != prequery ){
            total = datasource.getWordSize(code, curquery, searchroot);
            page = 0;
           /* try {
                datasource.open();
                datasource.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }*/
        }
        if(manageimthread != null && manageimthread.isAlive()){
            handler.removeCallbacks(manageimthread);
        }
        manageimthread = new Thread(new ManageImRunnable(handler, activity, code, curquery, searchroot,
                                                                            Lime.IM_MANAGE_DISPLAY_AMOUNT, offset));
        manageimthread.start();
        prequery = curquery;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        ((MainActivity) activity).onSectionAttached(
                getArguments().getInt(ARG_SECTION_NUMBER));

        this.code = getArguments().getString(ARG_SECTION_CODE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(this.manageimthread != null){
            this.handler.removeCallbacks(manageimthread);
        }
        if(this.progress.isShowing()){
            this.progress.cancel();
        }
        this.wordlist = null;
        this.SearchSrv.initialCache();
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

    public void updateGridView(List<Word> wordlist){

        this.wordlist = wordlist;

        int startrecord = Lime.IM_MANAGE_DISPLAY_AMOUNT * page;
        int endrecord = Lime.IM_MANAGE_DISPLAY_AMOUNT * (page + 1);

        if(page > 0){
            this.btnManageImPrevious.setEnabled(true);
        }else{
            this.btnManageImPrevious.setEnabled(false);
        }

        if(endrecord <= total){
            this.btnManageImNext.setEnabled(true);
        }else{
            this.btnManageImNext.setEnabled(false);
            endrecord = total;
        }

        if(total > 0){
            if(this.adapter == null){
                this.adapter = new ManageImAdapter(this.activity, wordlist);
                this.gridManageIm.setAdapter(this.adapter);
            }else{
                this.adapter.setList(wordlist);
                this.adapter.notifyDataSetChanged();
                this.gridManageIm.setSelection(0);
            }
        }else{
            if(this.adapter == null){
                this.adapter = new ManageImAdapter(this.activity, new ArrayList());
            }else{
                this.adapter.setList(new ArrayList());
            }
            this.adapter.notifyDataSetChanged();
            this.gridManageIm.setSelection(0);
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

    public void removeWord(int id){

        // Remove from the temp list
        for(int i = 0 ; i < total ; i++){
           if(id== this.wordlist.get(i).getId()){
               this.wordlist.remove(i);
               break;
           }
        }

        // Remove from the database
        String removesql = "DELETE FROM " + this.code + " WHERE " + Lime.DB_COLUMN_ID + " = '" + id + "'";

        datasource.remove(removesql);
        /*try {
            datasource.open();
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }*/

        total--;
        searchword();
        //updateGridView(this.wordlist);
    }

    public void addWord(String code, String code3r, String word) {

        // Add to database
        Word obj = new Word();
             obj.setCode(code);
             obj.setCode3r(code3r);
             obj.setWord(word);
             obj.setBasescore(0);
             obj.setScore(1);

        String insertsql = Word.getInsertQuery(this.code, obj);
        datasource.insert(insertsql);

       /* try {
            datasource.open();
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }*/

        total++;
        searchword();
        // Add to temp list
        /*page = 0;
        this.wordlist.add(0,obj);
        updateGridView(this.wordlist);*/
    }

    public void updateWord(int id, String code, String code3r, String word) {

        // remove from temp list
        for(int i = 0 ; i < total ; i++){
            if(id== this.wordlist.get(i).getId()){
                Word check = this.wordlist.get(i);
                     check.setCode(code);
                     check.setCode3r(code3r);
                     check.setWord(word);
                this.wordlist.remove(i);
                this.wordlist.add(i, check);
                break;
            }
        }

        // Update record in the database
        String updatesql = "UPDATE " + this.code + " SET ";
                updatesql += Lime.DB_COLUMN_CODE + " = \"" + Lime.formatSqlValue(code) + "\", ";
                if(!code3r.isEmpty()){
                    updatesql += Lime.DB_COLUMN_CODE3R + " = \"" + Lime.formatSqlValue(code3r) + "\", ";
                }
                updatesql += Lime.DB_COLUMN_WORD + " = \"" + Lime.formatSqlValue(word) + "\" ";
                updatesql += " WHERE " + Lime.DB_COLUMN_ID + " = \"" + id + "\"";

        datasource.update(updatesql);
        /*try {
            datasource.open();
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }*/
        searchword();
        //updateGridView(this.wordlist);
    }

    public void updateKeyboard(String keyboard) {

        if(keyboardlist == null){
            keyboardlist = datasource.getKeyboard();
           /* try {
                datasource.open();
                datasource.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }*/
        }
        for(Keyboard k: keyboardlist){
            if(k.getCode().equals(keyboard)){
                datasource.setImKeyboard(code, k);
                btnManageImKeyboard.setText(k.getDesc());
                /*try {
                    datasource.open();
                    datasource.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }*/
            }
        }

    }

}