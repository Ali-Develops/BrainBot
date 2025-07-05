package edu.ma.appx.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import edu.ma.appx.R;
import java.util.List;
import java.util.ArrayList; // Import ArrayList for updateHistory

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    // Interface for click events on history items
    public interface OnPromptClickListener {
        void onPromptClick(String prompt);
    }

    private List<String> historyList; // Made non-final to allow updates
    private final OnPromptClickListener listener;

    public HistoryAdapter(List<String> historyList, OnPromptClickListener listener) {
        // It's good practice to create a new ArrayList to avoid direct modification
        // of the list passed from the activity, preventing ConcurrentModificationException.
        this.historyList = new ArrayList<>(historyList);
        this.listener = listener;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the layout for a single history item
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        String prompt = historyList.get(position);
        holder.promptText.setText(prompt);
        // Set an OnClickListener for the entire item view
        holder.itemView.setOnClickListener(v -> listener.onPromptClick(prompt));
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    /**
     * Updates the adapter's data set with a new list of history prompts.
     * This method is crucial when the underlying historyList changes significantly
     * (e.g., items are added, removed, or reordered from MainActivity).
     *
     * @param newHistoryList The updated list of history prompts.
     */
    public void updateHistory(List<String> newHistoryList) {
        this.historyList.clear(); // Clear existing data
        this.historyList.addAll(newHistoryList); // Add all new data
        notifyDataSetChanged(); // Notify the RecyclerView to re-render
    }

    // ViewHolder class to hold references to the views for each item
    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView promptText;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            // Find the TextView responsible for displaying the prompt text
            promptText = itemView.findViewById(R.id.historyPromptText);
        }
    }
}