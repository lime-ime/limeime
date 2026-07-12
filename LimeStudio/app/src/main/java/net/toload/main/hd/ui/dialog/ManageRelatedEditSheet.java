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

package net.toload.main.hd.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;

import net.toload.main.hd.R;
import net.toload.main.hd.data.Related;
import net.toload.main.hd.ui.view.ManageRelatedFragment;

/**
 * Bottom sheet dialog for editing an existing related phrase entry.
 *
 * <p>Pre-populates fields from the given {@link Related} and delegates
 * update/delete operations to the hosting {@link ManageRelatedFragment}.
 */
public class ManageRelatedEditSheet extends BottomSheetDialogFragment {

    private ManageRelatedFragment hostFragment;
    private Related related;
    private int score;

    public static ManageRelatedEditSheet newInstance() {
        return new ManageRelatedEditSheet();
    }

    public void setFragment(ManageRelatedFragment fragment, Related related) {
        this.hostFragment = fragment;
        this.related = related;
        this.score = related.getBasescore();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_manage_related_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ImeAwareBottomSheet.applyInsets(view);

        TextInputEditText edtWord = view.findViewById(R.id.edt_word);
        TextInputEditText edtRelated = view.findViewById(R.id.edt_related);
        TextInputEditText edtScore = view.findViewById(R.id.edt_score);

        if (related != null) {
            edtWord.setText(related.getPword());
            edtRelated.setText(related.getCword());
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
                    if (hostFragment != null && related != null) {
                        hostFragment.removeRelated(related.getIdAsInt());
                    }
                    dismiss();
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
        });

        view.findViewById(R.id.btn_save).setOnClickListener(v -> {
            String pword = edtWord.getText() != null ? edtWord.getText().toString().trim() : "";
            String cword = edtRelated.getText() != null ? edtRelated.getText().toString().trim() : "";
            if (!validateInput(pword, cword)) {
                Toast.makeText(requireContext(), R.string.update_error, Toast.LENGTH_SHORT).show();
                return;
            }
            score = ManageSheetScoreInput.readScore(edtScore, score);
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_update_title)
                .setMessage(R.string.dialog_update_message)
                .setPositiveButton(R.string.dialog_confirm, (d, w) -> {
                    if (hostFragment != null && related != null) {
                        hostFragment.updateRelated(related.getIdAsInt(), pword, cword, score);
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

    private boolean validateInput(String pword, String cword) {
        return !pword.isEmpty() && !cword.isEmpty();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        hostFragment = null;
        related = null;
    }
}
