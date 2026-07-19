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

package org.limeime.ui.view;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.limeime.R;
import org.limeime.data.Related;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * RecyclerView adapter for displaying related-phrase items.
 *
 * <p>Uses {@link androidx.recyclerview.widget.ListAdapter} and
 * {@link androidx.recyclerview.widget.DiffUtil} for efficient updates.
 */
public class ManageRelatedAdapter extends ListAdapter<Related, ManageRelatedAdapter.ViewHolder> {

    private final Activity activity;
    private OnItemClickListener onItemClickListener;

    private static String truncateByCodePoint(String text, int maxCodePoints) {
        if (text == null || text.codePointCount(0, text.length()) <= maxCodePoints) {
            return text;
        }
        int end = text.offsetByCodePoints(0, maxCodePoints);
        return text.substring(0, end) + "...";
    }

    public interface OnItemClickListener {
        void onItemClick(Related related, int position);
    }

    public ManageRelatedAdapter(Activity activity) {
        super(DIFF_CALLBACK);
        this.activity = activity;
    }

    @Override
    public void submitList(List<Related> list) {
        // ListAdapter requires submitted lists to remain immutable while DiffUtil
        // compares them. The fragment also keeps its own mutable page list, so
        // submit a snapshot to prevent a delete from silently changing adapter
        // item positions before the refreshed database result arrives.
        super.submitList(list == null ? null : new ArrayList<>(list));
    }

    private static final DiffUtil.ItemCallback<Related> DIFF_CALLBACK = new DiffUtil.ItemCallback<Related>() {
        @Override
        public boolean areItemsTheSame(@NonNull Related oldItem, @NonNull Related newItem) {
            return oldItem.getIdAsInt() == newItem.getIdAsInt();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Related oldItem, @NonNull Related newItem) {
            return Objects.equals(oldItem.getPword(), newItem.getPword()) &&
                   Objects.equals(oldItem.getCword(), newItem.getCword()) &&
                   oldItem.getBasescore() == newItem.getBasescore() &&
                   oldItem.getUserscore() == newItem.getUserscore();
        }
    };

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.related, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Related r = getItem(position);
        if (r != null) {
            String pword = r.getPword();
            String cword = r.getCword();
            int freq = r.getBasescore();

            cword = truncateByCodePoint(cword, 10);

            holder.txtPword.setText(pword);
            holder.txtWord.setText(cword);
            holder.txtFreq.setText(String.format(java.util.Locale.US, "%,d", freq));

            holder.itemView.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(r, position);
                }
            });
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtPword;
        TextView txtWord;
        TextView txtFreq;

        ViewHolder(View itemView) {
            super(itemView);
            txtPword = itemView.findViewById(R.id.txtPword);
            txtWord = itemView.findViewById(R.id.txtWord);
            txtFreq = itemView.findViewById(R.id.txtFreq);
        }
    }
}
