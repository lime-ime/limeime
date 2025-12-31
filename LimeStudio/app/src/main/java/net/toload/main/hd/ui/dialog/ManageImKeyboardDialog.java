/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
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

package net.toload.main.hd.ui.dialog;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import net.toload.main.hd.R;
import net.toload.main.hd.ui.view.ManageImFragment;
import net.toload.main.hd.data.Keyboard;

import java.util.List;

/**
 * Dialog fragment for selecting a keyboard for an IM table.
 *
 * <p>Displays a list of available `Keyboard` options and notifies the
 * parent `ManageImFragment` when a selection is made.
 */
public class ManageImKeyboardDialog extends DialogFragment implements
    	AdapterView.OnItemClickListener {


    private List<Keyboard> keyboardlist;
	private ListView listSelectKeyboard;

	private String code;
	private ManageImFragment fragment;

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
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
	}

	@Override
	public void onCancel(@NonNull DialogInterface dialog) {
		super.onCancel(dialog);
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		this.setCancelable(false);
	}


	@Override
	public void onResume() {
		super.onResume();

        assert getDialog() != null;
        getDialog().setOnKeyListener((dialog, keyCode, event) -> {
            if ((keyCode == android.view.KeyEvent.KEYCODE_BACK)) {
                // To dismiss the fragment when the back-button is pressed.
                dismiss();
                return true;
            }
            // Otherwise, do nothing else
            else return false;
        });
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {

        assert getDialog() != null;
        getDialog().getWindow().setTitle(getResources().getString(R.string.manage_select_keyboard));

        View view = inflater.inflate(R.layout.fragment_dialog_keyboard, container, false);

		listSelectKeyboard = view.findViewById(R.id.listSelectKeyboard);
		
		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		String[] listitems;

			// Get keyboard list via fragment
			keyboardlist = fragment.getKeyboardList();

			listitems = new String[keyboardlist.size()];
			for(int i = 0; i < keyboardlist.size() ; i++){
				listitems[i] = keyboardlist.get(i).getDesc();
			}

		ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(),
                android.R.layout.simple_list_item_1, listitems);

		listSelectKeyboard.setAdapter(adapter);
		listSelectKeyboard.setOnItemClickListener(this);

	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle icicle) {
		super.onSaveInstanceState(icicle);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

		Keyboard keyboard = keyboardlist.get(position);
		// Use fragment to set the keyboard through controller
		fragment.setIMKeyboard(this.code, keyboard.getCode());

		fragment.updateKeyboard(keyboard.getCode());
		this.dismiss();
	}

	public void setFragment(ManageImFragment fragment, String code) {
		this.code = code;
		this.fragment = fragment;
	}
}
