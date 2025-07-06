package edu.ma.appx.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import edu.ma.appx.R;
import edu.ma.appx.models.Message;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<Message> messageList;
    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_AI = 2;

    public ChatAdapter(List<Message> messageList) {
        this.messageList = messageList;
    }

    @Override
    public int getItemViewType(int position) {
        return messageList.get(position).isUser() ? VIEW_TYPE_USER : VIEW_TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_user, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_ai, parent, false);
            return new AIViewHolder(view);
        }
    }


    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messageList.get(position);

        if (holder.getItemViewType() == VIEW_TYPE_USER) {
            ((UserViewHolder) holder).bind(message);
        } else {
            AIViewHolder aiHolder = (AIViewHolder) holder;
            aiHolder.bind(message);

            if (message.isLoading()) {
                aiHolder.loadingIndicator.setVisibility(View.VISIBLE);
            } else {
                aiHolder.loadingIndicator.setVisibility(View.GONE);
            }
        }
        if (!message.isAnimated()) {
            // Option 1: Simple Fade-in (recommended as a good default)
            holder.itemView.setAlpha(0f);
            holder.itemView.animate().alpha(1f).setDuration(400).start();
            message.setAnimated(true); // Mark as animated to prevent re-animating on scroll
        }
        // --- End Animation Logic ---
    }


    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView userText;

        UserViewHolder(View itemView) {
            super(itemView);
            userText = itemView.findViewById(R.id.userText);
        }

        public void bind(Message message) {
            userText.setText(message.getText());
        }
    }

    static class AIViewHolder extends RecyclerView.ViewHolder {
        ProgressBar loadingIndicator;
        TextView aiMessage;

        public AIViewHolder(@NonNull View itemView) {
            super(itemView);
            loadingIndicator = itemView.findViewById(R.id.loadingIndicator);
            aiMessage = itemView.findViewById(R.id.aiText);
        }

        public void bind(Message message) {
            aiMessage.setText(message.getText());
        }
    }
}