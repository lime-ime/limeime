package net.toload.main.hd.ui.adapter;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import net.toload.main.hd.data.Record;

/**
 * Test-only compatibility wrapper to expose ManageImAdapter under the expected package.
 * Delegates to the implementation in ui.view.
 */
public class ManageImAdapter extends net.toload.main.hd.ui.view.ManageImAdapter {
    // Expose DIFF_CALLBACK for reflection tests
    @SuppressWarnings("unused")
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
    
    // Helper method for truncating long words (visible to reflection tests)
    @SuppressWarnings("unused")
    private String truncateWord(String word, int maxLength) {
        if (word != null && word.length() > maxLength) {
            return word.substring(0, maxLength - 3) + "...";
        }
        return word;
    }
}
