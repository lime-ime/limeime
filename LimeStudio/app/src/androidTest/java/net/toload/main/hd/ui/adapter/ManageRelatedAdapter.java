package net.toload.main.hd.ui.adapter;

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

/**
 * Test-only adapter for displaying related phrase records in ManageRelated tests.
 */
public class ManageRelatedAdapter extends ListAdapter<Related, ManageRelatedAdapter.ViewHolder> {

    private OnItemClickListener clickListener;

    public interface OnItemClickListener {
        void onItemClick(Related related);
        void onItemLongClick(Related related);
    }

    private static final DiffUtil.ItemCallback<Related> DIFF_CALLBACK = new DiffUtil.ItemCallback<Related>() {
        @Override
        public boolean areItemsTheSame(@NonNull Related oldItem, @NonNull Related newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Related oldItem, @NonNull Related newItem) {
            return oldItem.getPword().equals(newItem.getPword()) &&
                   oldItem.getCword().equals(newItem.getCword());
        }
    };

    public ManageRelatedAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Related related = getItem(position);
        holder.bind(related);
        
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onItemClick(related);
            }
        });
        
        holder.itemView.setOnLongClickListener(v -> {
            if (clickListener != null) {
                clickListener.onItemLongClick(related);
                return true;
            }
            return false;
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView text1;
        private final TextView text2;

        ViewHolder(View itemView) {
            super(itemView);
            text1 = itemView.findViewById(android.R.id.text1);
            text2 = itemView.findViewById(android.R.id.text2);
        }

        void bind(Related related) {
            text1.setText(related.getPword());
            text2.setText(related.getCword());
        }
    }
}
