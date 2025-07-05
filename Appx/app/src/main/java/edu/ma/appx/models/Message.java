package edu.ma.appx.models;

public class Message {
    private String text;
    private boolean isUser;
    private boolean isLoading;
    private boolean isAnimated; // New field for animation control

    public Message(String text, boolean isUser, boolean isLoading) {
        this.text = text;
        this.isUser = isUser;
        this.isLoading = isLoading;
        this.isAnimated = false; // Initialize to false by default
    }

    // Constructor to allow setting isAnimated if loading from saved state
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

    // New getter and setter for animation flag
    public boolean isAnimated() {
        return isAnimated;
    }

    public void setAnimated(boolean animated) {
        isAnimated = animated;
    }

    // Optional: Add a toString() for easier debugging if needed
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