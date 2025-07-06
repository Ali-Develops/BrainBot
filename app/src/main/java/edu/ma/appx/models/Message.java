package edu.ma.appx.models;

import androidx.annotation.NonNull;

public class Message {
    private final String text;
    private final boolean isUser;
    private final boolean isLoading;
    private boolean isAnimated;

    public Message(String text, boolean isUser, boolean isLoading) {
        this.text = text;
        this.isUser = isUser;
        this.isLoading = isLoading;
        this.isAnimated = false;
    }

    public Message(String text, boolean isUser, boolean isLoading, boolean isAnimated) {
        this.text = text;
        this.isUser = isUser;
        this.isLoading = isLoading;
        this.isAnimated = isAnimated;
    }

    public String getText() {
        return text;
    }

    public boolean isUser() {
        return isUser;
    }

    public boolean isLoading() {
        return isLoading;
    }

    public boolean isAnimated() {
        return isAnimated;
    }

    public void setAnimated(boolean animated) {
        isAnimated = animated;
    }

    @NonNull
    @Override
    public String toString() {
        return "Message{" +
                "text='" + text + '\'' +
                ", isUser=" + isUser +
                ", isLoading=" + isLoading +
                ", isAnimated=" + isAnimated +
                '}';
    }
}
