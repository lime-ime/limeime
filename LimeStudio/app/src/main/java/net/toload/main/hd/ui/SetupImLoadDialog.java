package net.toload.main.hd.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import net.toload.main.hd.Lime;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.limedb.LimeDB;
import net.toload.main.hd.limesettings.DBServer;
import net.toload.main.hd.limesettings.LIMESelectFileAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Fragment used for managing interactions for and presentation of a navigation drawer.
 * See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented here.
 */

/**
 * A placeholder fragment containing a simple view.
 */
public class SetupImLoadDialog extends DialogFragment {

    // IM Log Tag
    private final String TAG = "SetupImLoadDialog";

    // Basic
    private SetupImHandler handler;

    // Default
    Button btnSetupImDialogCustom;
    Button btnSetupImDialogLoad1;
    Button btnSetupImDialogLoad2;
    Button btnSetupImDialogLoad3;
    Button btnSetupImDialogCancel;

    private ConnectivityManager connManager;

    private String imtype = null;
    private int imcount = 0;

    private SetupImLoadDialog frgdialog;
    private LimeDB datasource;
    private DBServer DBSrv = null;
    private Activity activity;
    private LIMEPreferenceManager mLIMEPref;

    private static String IM_TYPE = "IM_TYPE";

    // Select File
    private LIMESelectFileAdapter adapter;
    private ListView listview;
    private LinearLayout toplayout;
    List<File> flist;

    private Thread loadthread;

    public SetupImLoadDialog(){}

    public void setHandler(SetupImHandler handler){
        this.handler = handler;
    }

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static SetupImLoadDialog newInstance(String imtype, SetupImHandler handler) {
        SetupImLoadDialog frg = new SetupImLoadDialog();
        Bundle args = new Bundle();
               args.putString(IM_TYPE, imtype);
               frg.setArguments(args);
               frg.setHandler(handler);
        return frg;
    }


    @Override
    public void onResume() {
        super.onResume();
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        frgdialog = this;
        imtype = getArguments().getString(IM_TYPE);
        activity = getActivity();
        datasource = new LimeDB(activity);
        DBSrv = new DBServer(activity);
        mLIMEPref = new LIMEPreferenceManager(activity);

        connManager = (ConnectivityManager) activity.getSystemService(
                SetupImLoadDialog.this.activity.CONNECTIVITY_SERVICE);

        imcount = datasource.count(imtype);
        /*try {
            datasource.open();
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }*/

        View rootView = inflater.inflate(R.layout.fragment_dialog_im, container, false);

        btnSetupImDialogCustom = (Button) rootView.findViewById(R.id.btnSetupImDialogCustom);
        btnSetupImDialogLoad1 = (Button) rootView.findViewById(R.id.btnSetupImDialogLoad1);
        btnSetupImDialogLoad2 = (Button) rootView.findViewById(R.id.btnSetupImDialogLoad2);
        btnSetupImDialogLoad3 = (Button) rootView.findViewById(R.id.btnSetupImDialogLoad3);

        if(imcount > 0){

            getDialog().getWindow().setTitle(getResources().getString(R.string.setup_im_dialog_title_remove));

            btnSetupImDialogLoad1.setText(getResources().getString(R.string.setup_im_dialog_remove));
            btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
                    alertDialog.setMessage(activity.getResources().getString(R.string.setup_im_dialog_remove_confirm_message));
                    //alertDialog.setIcon(R.drawable.);
                    alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.dialog_confirm),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    try {
                                        DBSrv.resetMapping(imtype);
                                        if(imtype.equals(Lime.DB_TABLE_CUSTOM)){
                                            handler.updateCustomButton();
                                        }
                                    } catch (RemoteException e) {
                                        e.printStackTrace();
                                    }
                                    handler.initialImButtons();
                                    dismiss();
                                    frgdialog.dismiss();
                                }
                            });
                    alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getResources().getString(R.string.dialog_cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();
                }
            });
            btnSetupImDialogLoad2.setVisibility(View.GONE);
            btnSetupImDialogLoad3.setVisibility(View.GONE);
            btnSetupImDialogCustom.setVisibility(View.GONE);

        } else {

            getDialog().getWindow().setTitle(getResources().getString(R.string.setup_im_dialog_title));

            btnSetupImDialogCustom.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectMappingFile(Lime.DATABASE_FOLDER_EXTERNAL, imtype);
                    handler.initialImButtons();
                    dismiss();
                }
            });

            if (imtype.equals(Lime.DB_TABLE_PHONETIC)) {

                btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_phonetic));
                btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAndLoadIm(Lime.DB_TABLE_PHONETIC, Lime.IM_PHONETIC);
                    }
                });
                btnSetupImDialogLoad2.setText(getResources().getString(R.string.l3_im_download_from_phonetic_adv));
                btnSetupImDialogLoad2.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAndLoadIm(Lime.DB_TABLE_PHONETIC, Lime.IM_PHONETIC_ADV);
                    }
                });
                btnSetupImDialogLoad3.setVisibility(View.GONE);

            } else if (imtype.equals(Lime.DB_TABLE_CJ)) {

                btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_cj));
                btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAndLoadIm(Lime.DB_TABLE_CJ, Lime.IM_CJ);
                    }
                });
                btnSetupImDialogLoad2.setText(getResources().getString(R.string.l3_im_download_from_cjk_hk_cj));
                btnSetupImDialogLoad2.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAndLoadIm(Lime.DB_TABLE_CJ, Lime.IM_CJHK);
                    }
                });
                btnSetupImDialogLoad3.setVisibility(View.GONE);

            } else if (imtype.equals(Lime.DB_TABLE_CJ5)) {

                btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_cj5));
                btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAndLoadIm(Lime.DB_TABLE_CJ5, Lime.IM_CJ5);
                    }
                });
                btnSetupImDialogLoad2.setVisibility(View.GONE);
                btnSetupImDialogLoad3.setVisibility(View.GONE);

            } else if (imtype.equals(Lime.DB_TABLE_SCJ)) {

                btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_scj));
                btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAndLoadIm(Lime.DB_TABLE_SCJ, Lime.IM_SCJ);
                    }
                });
                btnSetupImDialogLoad2.setVisibility(View.GONE);
                btnSetupImDialogLoad3.setVisibility(View.GONE);

            } else if (imtype.equals(Lime.DB_TABLE_ECJ)) {

                btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_ecj));
                btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAndLoadIm(Lime.DB_TABLE_ECJ, Lime.IM_ECJ);
                    }
                });
                btnSetupImDialogLoad2.setText(getResources().getString(R.string.l3_im_download_from_cjk_hk_ecj));
                btnSetupImDialogLoad2.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAndLoadIm(Lime.DB_TABLE_ECJ, Lime.IM_ECJHK);
                    }
                });
                btnSetupImDialogLoad3.setVisibility(View.GONE);

            } else if (imtype.equals(Lime.DB_TABLE_DAYI)) {

                btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_dayi));
                btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAndLoadIm(Lime.DB_TABLE_DAYI, Lime.IM_DAYI);
                    }
                });
                btnSetupImDialogLoad2.setVisibility(View.GONE);
                btnSetupImDialogLoad3.setVisibility(View.GONE);

            } else if (imtype.equals(Lime.DB_TABLE_EZ)) {

                btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_ez));
                btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAndLoadIm(Lime.DB_TABLE_EZ, Lime.IM_EZ);
                    }
                });
                btnSetupImDialogLoad2.setVisibility(View.GONE);
                btnSetupImDialogLoad3.setVisibility(View.GONE);

            } else if (imtype.equals(Lime.DB_TABLE_ARRAY)) {

                btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_array));
                btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAndLoadIm(Lime.DB_TABLE_ARRAY, Lime.IM_ARRAY);
                    }
                });
                btnSetupImDialogLoad2.setVisibility(View.GONE);
                btnSetupImDialogLoad3.setVisibility(View.GONE);

            } else if (imtype.equals(Lime.DB_TABLE_ARRAY10)) {

                btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_array10));
                btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAndLoadIm(Lime.DB_TABLE_ARRAY10, Lime.IM_ARRAY10);
                    }
                });
                btnSetupImDialogLoad2.setVisibility(View.GONE);
                btnSetupImDialogLoad3.setVisibility(View.GONE);

            } else if (imtype.equals(Lime.DB_TABLE_PINYIN)) {

                btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_pinyin_big5));
                btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAndLoadIm(Lime.DB_TABLE_PINYIN, Lime.IM_PINYIN);
                    }
                });
                btnSetupImDialogLoad2.setText(getResources().getString(R.string.l3_im_download_from_pinyin_gb));
                btnSetupImDialogLoad2.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAndLoadIm(Lime.DB_TABLE_PINYIN, Lime.IM_PINYINGB);
                    }
                });
                btnSetupImDialogLoad3.setVisibility(View.GONE);

            } else if (imtype.equals(Lime.DB_TABLE_WB)) {

                btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_wb));
                btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadAndLoadIm(Lime.DB_TABLE_WB, Lime.IM_WB);
                    }
                });
                btnSetupImDialogLoad2.setVisibility(View.GONE);
                btnSetupImDialogLoad3.setVisibility(View.GONE);
            } else {
                btnSetupImDialogLoad1.setVisibility(View.GONE);
                btnSetupImDialogLoad2.setVisibility(View.GONE);
                btnSetupImDialogLoad3.setVisibility(View.GONE);
            }
        }

        btnSetupImDialogCancel = (Button) rootView.findViewById(R.id.btnSetupImDialogCancel);
        btnSetupImDialogCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        return rootView;
    }

    public void selectMappingFile(String srcpath, String tablename) {

        final Dialog dialog = new Dialog(activity);

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.target);
        dialog.setCancelable(false);
        Button button = (Button) dialog.findViewById(R.id.btn_loading_sync_cancel);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        listview = (ListView) dialog.findViewById(R.id.listview_loading_target);
        toplayout = (LinearLayout) dialog.findViewById(R.id.linearlayout_loading_confirm_top);
        listview.setAdapter(getAdapter(new File(Lime.DATABASE_FOLDER_EXTERNAL)));

        createNavigationButtons(new File(Lime.DATABASE_FOLDER_EXTERNAL));
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener(){

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1,
                                    int position, long arg3) {
                File f = flist.get(position);
                if(f.isDirectory()){
                    listview.setAdapter(getAdapter(f));
                    createNavigationButtons(f);
                }else{
                    getAvailableFiles(f.getAbsolutePath());
                    dialog.dismiss();
                }
            }

        });
        dialog.show();
    }

    private void createNavigationButtons(final File dir) {

        // Clean Top Area
        toplayout.removeAllViews();

        // Create Navigation Buttons
        String path = dir.getAbsolutePath();
        String[] pathlist = path.split("\\/");

        String pathconstruct = "/";
        if (pathlist.length > 0) {
            for (String p : pathlist) {
                if (!p.equals("") && !p.equals("/")) {
                    pathconstruct += p + "/";
                } else {
                    p = "/";
                }
                final String actpath = pathconstruct;
                Button b = new Button(activity);
                b.setText(p);
                b.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                b.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View arg0) {
                        createNavigationButtons(new File(actpath));
                        flist = getAvailableFiles(actpath);
                        listview.setAdapter(getAdapter(flist));
                    }
                });

                toplayout.addView(b);
            }
        } else {
            //final String actpath = pathconstruct;
            Button b = new Button(activity);
            b.setText("/");
            b.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View arg0) {
                    createNavigationButtons(new File("/"));
                    flist = getAvailableFiles("/");
                    listview.setAdapter(getAdapter(flist));
                }
            });
            toplayout.addView(b);
            flist = getAvailableFiles("/");
            listview.setAdapter(getAdapter(flist));
        }
    }

    public LIMESelectFileAdapter getAdapter(List<File> list) {
        return new LIMESelectFileAdapter(activity, list);
    }

    public LIMESelectFileAdapter getAdapter(File path) {
        flist = getAvailableFiles(path.getAbsolutePath());
        return new LIMESelectFileAdapter(activity, flist);
    }

    public void downloadAndLoadIm(String code, String type){
        if (connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()) {

            String url = null;

            if(type.equals(Lime.IM_ARRAY )){
                url = Lime.DATABASE_CLOUD_IM_ARRAY;
            }else if(type.equals(Lime.IM_ARRAY10 )){
                url = Lime.DATABASE_CLOUD_IM_ARRAY10;
            }else if(type.equals(Lime.IM_CJ )){
                url = Lime.DATABASE_CLOUD_IM_CJ;
            }else if(type.equals(Lime.IM_CJHK )){
                url = Lime.DATABASE_CLOUD_IM_CJHK;
            }else if(type.equals(Lime.IM_CJ5 )){
                url = Lime.DATABASE_CLOUD_IM_CJ5;
            }else if(type.equals(Lime.IM_DAYI )){
                url = Lime.DATABASE_CLOUD_IM_DAYI;
            }else if(type.equals(Lime.IM_ECJ )){
                url = Lime.DATABASE_CLOUD_IM_ECJ;
            }else if(type.equals(Lime.IM_ECJHK )){
                url = Lime.DATABASE_CLOUD_IM_ECJHK;
            }else if(type.equals(Lime.IM_EZ )){
                url = Lime.DATABASE_CLOUD_IM_EZ;
            }else if(type.equals(Lime.IM_PHONETIC )){
                url = Lime.DATABASE_CLOUD_IM_PHONETIC;
            }else if(type.equals(Lime.IM_PHONETIC_ADV )){
                url = Lime.DATABASE_CLOUD_IM_PHONETICCOMPLETE;
            }else if(type.equals(Lime.IM_PINYIN )){
                url = Lime.DATABASE_CLOUD_IM_PINYIN;
            }else if(type.equals(Lime.IM_PINYINGB )){
                url = Lime.DATABASE_CLOUD_IM_PINYINGB;
            }else if(type.equals(Lime.IM_SCJ )){
                url = Lime.DATABASE_CLOUD_IM_SCJ;
            }else if(type.equals(Lime.IM_WB )){
                url = Lime.DATABASE_CLOUD_IM_WB;
            }

            loadthread = new Thread(new SetupImLoadRunnable(getActivity(), handler, code, url));
            loadthread.start();

            dismiss();
        }else{
            showToastMessage(getResources().getString(R.string.l3_tab_initial_error), Toast.LENGTH_LONG);
        }
    }

    public void showToastMessage(String msg, int length) {
        Toast toast = Toast.makeText(activity, msg, length);
        toast.show();
    }

    private List<File> getAvailableFiles(String path) {

        List<File> templist = new ArrayList<File>();
        List<File> list = new ArrayList<File>();
        File check = new File(path);

        if (check.exists() && check.isDirectory()) {

            for(File f: check.listFiles()){
                if(f.canRead()){
                    if(!f.isDirectory()){
                        if( f.getName().toLowerCase().endsWith("cin") ||
                                f.getName().toLowerCase().endsWith("lime") ||
                                f.getName().toLowerCase().endsWith("txt")){
                            list.add(f);
                        }
                    }else{
                        list.add(f);
                    }
                }
            }

            List<File> folders = new ArrayList<File>();
            List<File> files = new ArrayList<File>();
            for(File f: list){
                if(f.isDirectory()){
                    folders.add(f);
                }else{
                    files.add(f);
                }
            }

            List<File> result = new ArrayList<File>();
            Collections.sort(folders, SORT_FILENAME);
            Collections.reverse(folders);
            result.addAll(folders);
            Collections.sort(files, SORT_FILENAME);
            Collections.reverse(files);
            result.addAll(files);

            return result;

        } else {
            loadMapping(check);
        }
        return templist;
    }

    static final Comparator<File> SORT_FILENAME = new Comparator<File>() {
        public int compare(File e1, File e2) {
            return e2.getName().compareTo(e1.getName());
        }
    };

    public void loadMapping(File unit) {
        try {
            DBSrv.loadMapping(unit.getAbsolutePath(), imtype);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        handler.startLoadingWindow(imtype);
    }

}