package edu.ma.appx.adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import edu.ma.appx.R;

public class SuggestedPromptsAdapter extends RecyclerView.Adapter<SuggestedPromptsAdapter.PromptViewHolder> {
    private final List<String> prompts;
    private final OnPromptClickListener listener;

    public interface OnPromptClickListener {
        void onPromptClick(String prompt);
    }

    public SuggestedPromptsAdapter(List<String> prompts, OnPromptClickListener listener) {
        this.prompts = prompts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PromptViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_prompt, parent, false);
        return new PromptViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PromptViewHolder holder, int position) {
        String prompt = prompts.get(position);
        holder.promptText.setText(prompt);
        holder.promptText.setOnClickListener(v -> listener.onPromptClick(prompt));
    }

    @Override
    public int getItemCount() {
        return prompts.size();
    }

    static class PromptViewHolder extends RecyclerView.ViewHolder {
        TextView promptText;

        public PromptViewHolder(@NonNull View itemView) {
            super(itemView);
            promptText = itemView.findViewById(R.id.promptText);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updatePrompts(List<String> newPrompts) {
        prompts.clear();
        prompts.addAll(newPrompts);
        notifyDataSetChanged();
    }
}
