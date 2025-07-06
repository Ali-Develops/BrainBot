package edu.ma.appx.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import edu.ma.appx.R;
import java.util.List;
import java.util.ArrayList;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    public interface OnPromptClickListener {
        void onPromptClick(String prompt);
        void onPromptLongClick(String prompt);
    }

    private final List<String> historyList;
    private final OnPromptClickListener listener;

    public HistoryAdapter(List<String> historyList, OnPromptClickListener listener) {
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

        holder.itemView.setOnClickListener(v -> listener.onPromptClick(prompt));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onPromptLongClick(prompt); // Call the long-click listener
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }


    @SuppressLint("NotifyDataSetChanged")
    public void updateHistory(List<String> newHistoryList) {
        this.historyList.clear();
        this.historyList.addAll(newHistoryList);
        notifyDataSetChanged();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView promptText;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            promptText = itemView.findViewById(R.id.historyPromptText);
        }
    }
}