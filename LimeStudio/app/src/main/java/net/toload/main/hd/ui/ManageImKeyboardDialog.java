package net.toload.main.hd.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import net.toload.main.hd.R;
import net.toload.main.hd.data.Keyboard;
import net.toload.main.hd.limedb.LimeDB;

import java.util.List;

public class ManageImKeyboardDialog extends DialogFragment implements
		AdapterView.OnItemClickListener {

	private Activity activity;
	private View view;


	private List<Keyboard> keyboardlist;
	private ListView listSelectKeyboard;

	private LimeDB datasource;
	private String code;
	private ManageImHandler handler;

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	public static ManageImKeyboardDialog newInstance() {
		ManageImKeyboardDialog btd = new ManageImKeyboardDialog();
						       btd.setCancelable(true);
		return btd;
	}

	public void setCode(String code){
		this.code = code;
	}

	@Override
	public void onAttach(Activity act) {
		super.onAttach(act);
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		super.onCancel(dialog);
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		this.setCancelable(false);
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {

		getDialog().getWindow().setTitle(getResources().getString(R.string.manage_select_keyboard));

		activity = getActivity();
		datasource = new LimeDB(this.activity);

		view = inflater.inflate(R.layout.fragment_dialog_keyboard, container, false);

		listSelectKeyboard = (ListView) view.findViewById(R.id.listSelectKeyboard);
		
		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		String[] listitems = new String[]{};

			//datasource.open();
			keyboardlist = datasource.getKeyboard();
			//datasource.close();

			listitems = new String[keyboardlist.size()];
			for(int i = 0; i < keyboardlist.size() ; i++){
				listitems[i] = keyboardlist.get(i).getDesc();
			}

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
				android.R.layout.simple_list_item_1, listitems);

		listSelectKeyboard.setAdapter(adapter);
		listSelectKeyboard.setOnItemClickListener(this);

	}

	@Override
	public void onSaveInstanceState(Bundle icicle) {
		super.onSaveInstanceState(icicle);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

		Keyboard keyboard = keyboardlist.get(position);
		datasource.setImKeyboard(this.code, keyboard);

		//try {
			//datasource.open();
			//datasource.close();
		/*} catch (SQLException e) {
			e.printStackTrace();
		}*/

		handler.updateKeyboardButton(keyboard.getCode());
		this.dismiss();
	}

	public void setHandler(ManageImHandler handler, String code) {
		this.code = code;
		this.handler = handler;
	}
}
