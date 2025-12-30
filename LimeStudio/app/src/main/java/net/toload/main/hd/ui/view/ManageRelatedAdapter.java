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

package net.toload.main.hd.ui.view;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import net.toload.main.hd.R;
import net.toload.main.hd.data.Related;

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

    public interface OnItemClickListener {
        void onItemClick(Related related, int position);
    }

    public ManageRelatedAdapter(Activity activity) {
        super(DIFF_CALLBACK);
        this.activity = activity;
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
            String text = pword + cword;

            int freq = r.getBasescore();

            if (text.length() > 12) {
                text = text.substring(0, 10) + "...";
            }

            holder.txtWord.setText(text);
            holder.txtFreq.setText(activity.getString(R.string.number_format, freq));

            holder.itemView.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(r, position);
                }
            });
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtWord;
        TextView txtFreq;

        ViewHolder(View itemView) {
            super(itemView);
            txtWord = itemView.findViewById(R.id.txtWord);
            txtFreq = itemView.findViewById(R.id.txtFreq);
        }
    }
}
