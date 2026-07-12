/*
 *
 *  *
 *  **    Copyright 2026, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
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

package org.limeime.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;

import org.limeime.R;
import org.limeime.ui.view.ManageImFragment;

/**
 * Bottom sheet dialog for adding a new IM mapping record.
 *
 * <p>Collects code, word and score and delegates creation to the hosting
 * {@link ManageImFragment}.
 */
public class ManageImAddSheet extends BottomSheetDialogFragment {

    private ManageImFragment hostFragment;
    private int score = 0;

    public static ManageImAddSheet newInstance() {
        return new ManageImAddSheet();
    }

    public void setFragment(ManageImFragment fragment) {
        this.hostFragment = fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_manage_im_add, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ImeAwareBottomSheet.applyInsets(view);

        TextInputEditText edtCode = view.findViewById(R.id.edt_code);
        TextInputEditText edtWord = view.findViewById(R.id.edt_word);
        TextInputEditText edtScore = view.findViewById(R.id.edt_score);
        ManageSheetScoreInput.setScore(edtScore, score);

        view.findViewById(R.id.btn_minus).setOnClickListener(v -> {
            score = ManageSheetScoreInput.decrement(edtScore, score);
        });
        view.findViewById(R.id.btn_plus).setOnClickListener(v -> {
            score = ManageSheetScoreInput.increment(edtScore, score);
        });
        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            dismiss();
        });

        view.findViewById(R.id.btn_save).setOnClickListener(v -> {
            String code = edtCode.getText() != null ? edtCode.getText().toString().trim() : "";
            String word = edtWord.getText() != null ? edtWord.getText().toString().trim() : "";
            if (!validateInput(code, word)) {
                Toast.makeText(requireContext(), R.string.insert_error, Toast.LENGTH_SHORT).show();
                return;
            }
            score = ManageSheetScoreInput.readScore(edtScore, score);
            if (hostFragment != null) {
                hostFragment.addRecord(code, score, word);
            }
            dismiss();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        ImeAwareBottomSheet.expandForIme(this);
    }

    private boolean validateInput(String code, String word) {
        return !code.isEmpty() && !word.isEmpty();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        hostFragment = null;
    }
}
