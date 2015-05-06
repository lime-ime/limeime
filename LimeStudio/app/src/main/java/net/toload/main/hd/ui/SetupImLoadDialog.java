package net.toload.main.hd.ui;

import android.app.Activity;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import net.toload.main.hd.Lime;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.limesettings.DBServer;

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
    //private SetupImHandler handler;

    // Default
    Button btnSetupImDialogLoad1;
    Button btnSetupImDialogLoad2;
    Button btnSetupImDialogLoad3;
    Button btnSetupImDialogCancel;

    private ConnectivityManager connManager;

    private String imtype = null;

    private DBServer DBSrv = null;
    private Activity activity;
    private LIMEPreferenceManager mLIMEPref;

    private static String IM_TYPE = "IM_TYPE";


    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static SetupImLoadDialog newInstance(String imtype) {
        SetupImLoadDialog frg = new SetupImLoadDialog();
        Bundle args = new Bundle();
                 args.putString(IM_TYPE, imtype);
                frg.setArguments(args);
        return frg;
    }


    @Override
    public void onResume() {
        super.onResume();
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        getDialog().getWindow().setTitle(getResources().getString(R.string.setup_im_dialog_title));

        imtype = getArguments().getString(IM_TYPE);
        activity = getActivity();

        DBSrv = new DBServer(activity);
        mLIMEPref = new LIMEPreferenceManager(activity);

        connManager = (ConnectivityManager) SetupImLoadDialog.this.activity.getSystemService(
                SetupImLoadDialog.this.activity.CONNECTIVITY_SERVICE);

        View rootView = inflater.inflate(R.layout.fragment_dialog_im, container, false);

        btnSetupImDialogLoad1 = (Button) rootView.findViewById(R.id.btnSetupImDialogLoad1);
        btnSetupImDialogLoad2 = (Button) rootView.findViewById(R.id.btnSetupImDialogLoad2);
        btnSetupImDialogLoad3 = (Button) rootView.findViewById(R.id.btnSetupImDialogLoad3);

        if(imtype.equals(Lime.DB_TABLE_PHONETIC)){

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

        }else if(imtype.equals(Lime.DB_TABLE_CJ)){

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

        }else if(imtype.equals(Lime.DB_TABLE_CJ5)){

            btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_cj5));
            btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    downloadAndLoadIm(Lime.DB_TABLE_CJ5, Lime.IM_CJ5);
                }
            });
            btnSetupImDialogLoad2.setVisibility(View.GONE);
            btnSetupImDialogLoad3.setVisibility(View.GONE);

        }else if(imtype.equals(Lime.DB_TABLE_SCJ)){

            btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_scj));
            btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    downloadAndLoadIm(Lime.DB_TABLE_SCJ, Lime.IM_SCJ);
                }
            });
            btnSetupImDialogLoad2.setVisibility(View.GONE);
            btnSetupImDialogLoad3.setVisibility(View.GONE);

        }else if(imtype.equals(Lime.DB_TABLE_ECJ)){

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

        }else if(imtype.equals(Lime.DB_TABLE_DAYI)){

            btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_dayi));
            btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    downloadAndLoadIm(Lime.DB_TABLE_DAYI, Lime.IM_DAYI);
                }
            });
            btnSetupImDialogLoad2.setVisibility(View.GONE);
            btnSetupImDialogLoad3.setVisibility(View.GONE);

        }else if(imtype.equals(Lime.DB_TABLE_EZ)){

            btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_ez));
            btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    downloadAndLoadIm(Lime.DB_TABLE_EZ, Lime.IM_EZ);
                }
            });
            btnSetupImDialogLoad2.setVisibility(View.GONE);
            btnSetupImDialogLoad3.setVisibility(View.GONE);

        }else if(imtype.equals(Lime.DB_TABLE_ARRAY)){

            btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_array));
            btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    downloadAndLoadIm(Lime.DB_TABLE_ARRAY, Lime.IM_ARRAY);
                }
            });
            btnSetupImDialogLoad2.setVisibility(View.GONE);
            btnSetupImDialogLoad3.setVisibility(View.GONE);

        }else if(imtype.equals(Lime.DB_TABLE_ARRAY10)){

            btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_array10));
            btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    downloadAndLoadIm(Lime.DB_TABLE_ARRAY10, Lime.IM_ARRAY10);
                }
            });
            btnSetupImDialogLoad2.setVisibility(View.GONE);
            btnSetupImDialogLoad3.setVisibility(View.GONE);

        }else if(imtype.equals(Lime.DB_TABLE_PINYIN)){

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

        }else if(imtype.equals(Lime.DB_TABLE_WB)){

            btnSetupImDialogLoad1.setText(getResources().getString(R.string.l3_im_download_from_wb));
            btnSetupImDialogLoad1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    downloadAndLoadIm(Lime.DB_TABLE_WB, Lime.IM_WB);
                }
            });
            btnSetupImDialogLoad2.setVisibility(View.GONE);
            btnSetupImDialogLoad3.setVisibility(View.GONE);

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

    public void downloadAndLoadIm(String im, String type){
        if (connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()) {

        }else{
            showToastMessage(getResources().getString(R.string.l3_tab_initial_error), Toast.LENGTH_LONG);
        }
    }

    public void showToastMessage(String msg, int length) {
        Toast toast = Toast.makeText(activity, msg, length);
        toast.show();
    }


}