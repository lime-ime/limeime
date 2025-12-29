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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import net.toload.main.hd.R;
import net.toload.main.hd.data.Record;

/**
 * RecyclerView adapter for ManageIm grid using ListAdapter with DiffUtil for efficient updates.
 */
public class ManageImAdapter extends ListAdapter<Record, ManageImAdapter.ViewHolder> {

    private OnItemClickListener onItemClickListener;

    public interface OnItemClickListener {
        void onItemClick(Record record, int position);
    }

    public ManageImAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<Record> DIFF_CALLBACK = new DiffUtil.ItemCallback<Record>() {
        @Override
        public boolean areItemsTheSame(@NonNull Record oldItem, @NonNull Record newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Record oldItem, @NonNull Record newItem) {
            return oldItem.getCode().equals(newItem.getCode()) &&
                   oldItem.getWord().equals(newItem.getWord()) &&
                   oldItem.getScore() == newItem.getScore();
        }
    };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.word, parent, false);
        return new ViewHolder(view);
    }

    private static final int MAX_WORD_LENGTH = 12;
    private static final String TRUNCATION_SUFFIX = "...";

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Record record = getItem(position);
        if (record != null) {
            String wordtext = record.getWord() != null ? record.getWord() : "";
            if (wordtext.length() > MAX_WORD_LENGTH) {
                wordtext = wordtext.substring(0, MAX_WORD_LENGTH - TRUNCATION_SUFFIX.length()) 
                    + TRUNCATION_SUFFIX;
            }
            holder.txtCode.setText(record.getCode());
            holder.txtWord.setText(wordtext);

            holder.itemView.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(record, holder.getAdapterPosition());
                }
            });
        }
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtWord;
        TextView txtCode;

        ViewHolder(View itemView) {
            super(itemView);
            txtWord = itemView.findViewById(R.id.txtWord);
            txtCode = itemView.findViewById(R.id.txtCode);
        }
    }
}
