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
import org.limeime.data.Record;
import org.limeime.ui.view.ManageImFragment;

/**
 * Bottom sheet dialog for editing an existing IM mapping record.
 *
 * <p>Pre-populates fields from the given {@link Record} and delegates
 * update/delete operations to the hosting {@link ManageImFragment}.
 */
public class ManageImEditSheet extends BottomSheetDialogFragment {

    private ManageImFragment hostFragment;
    private Record record;
    private int score;

    public static ManageImEditSheet newInstance() {
        return new ManageImEditSheet();
    }

    public void setFragment(ManageImFragment fragment, Record record) {
        this.hostFragment = fragment;
        this.record = record;
        this.score = record.getScore();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_manage_im_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ImeAwareBottomSheet.applyInsets(view);

        TextInputEditText edtCode = view.findViewById(R.id.edt_code);
        TextInputEditText edtWord = view.findViewById(R.id.edt_word);
        TextInputEditText edtScore = view.findViewById(R.id.edt_score);

        if (record != null) {
            edtCode.setText(record.getCode());
            edtWord.setText(record.getWord());
            ManageSheetScoreInput.setScore(edtScore, score);
        }

        view.findViewById(R.id.btn_minus).setOnClickListener(v -> {
            score = ManageSheetScoreInput.decrement(edtScore, score);
        });
        view.findViewById(R.id.btn_plus).setOnClickListener(v -> {
            score = ManageSheetScoreInput.increment(edtScore, score);
        });
        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            dismiss();
        });

        view.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(R.string.dialog_delete_message)
                .setPositiveButton(R.string.dialog_confirm, (d, w) -> {
                    if (hostFragment != null && record != null) {
                        hostFragment.removeRecord(record.getIdAsInt());
                    }
                    dismiss();
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
        });

        view.findViewById(R.id.btn_save).setOnClickListener(v -> {
            String code = edtCode.getText() != null ? edtCode.getText().toString().trim() : "";
            String word = edtWord.getText() != null ? edtWord.getText().toString().trim() : "";
            if (!validateInput(code, word)) {
                Toast.makeText(requireContext(), R.string.update_error, Toast.LENGTH_SHORT).show();
                return;
            }
            score = ManageSheetScoreInput.readScore(edtScore, score);
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_update_title)
                .setMessage(R.string.dialog_update_message)
                .setPositiveButton(R.string.dialog_confirm, (d, w) -> {
                    if (hostFragment != null && record != null) {
                        hostFragment.updateRecord(record.getIdAsInt(), code, score, word);
                    }
                    dismiss();
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
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
        record = null;
    }
}
