package edu.ma.appx.services;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;
import edu.ma.appx.activities.MainActivity;
import edu.ma.appx.models.Message;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.FieldValue;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

public class FirebaseService {

    private static final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    @SuppressLint("StaticFieldLeak")
    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface FirestoreCallback<T> {
        void onSuccess(T result);
        void onFailure(Exception e);
    }

    public static void signUpUser(Activity activity, String email, String password, Runnable onComplete) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(activity, task -> {
                    onComplete.run(); // close loading dialogue
                    if (task.isSuccessful()) {
                        activity.startActivity(new Intent(activity, MainActivity.class));
                        activity.finish();
                    } else {
                        Toast.makeText(activity, "Signup failed: " + Objects.requireNonNull(task.getException()).getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    public static void loginUser(Activity activity, String email, String password, Runnable onComplete) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(activity, task -> {
                    onComplete.run(); // close loading dialogue
                    if (task.isSuccessful()) {
                        activity.startActivity(new Intent(activity, MainActivity.class));
                        activity.finish();
                    } else {
                        Exception e = task.getException();
                        String message;

                        if (e instanceof FirebaseAuthInvalidCredentialsException) {
                            message = "Invalid email or password.";
                        } else {
                            assert e != null;
                            message = "Login failed: " + e.getMessage();
                        }

                        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    public static FirebaseUser getCurrentUser() {
        return FirebaseAuth.getInstance().getCurrentUser();
    }

    public static boolean isUserLoggedIn() {
        return getCurrentUser() != null;
    }

    public static void logout() {
        mAuth.signOut();
    }

    public static void getAllChats(FirestoreCallback<Map<String, String>> callback) {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            callback.onFailure(new Exception("User not logged in"));
            return;
        }

        CollectionReference chatsRef = db.collection("users")
                .document(user.getUid())
                .collection("chats");

        chatsRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Map<String, String> chatMap = new LinkedHashMap<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String title = doc.getString("title");
                        if (title == null) {
                            title = "(Untitled Chat)";
                        }
                        chatMap.put(doc.getId(), title);
                    }
                    callback.onSuccess(chatMap);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public static void createNewChat(String messageText, boolean isUser, FirestoreCallback<String> callback) {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            callback.onFailure(new Exception("User not logged in"));
            return;
        }

        CollectionReference chatsRef = db.collection("users")
                .document(user.getUid())
                .collection("chats");

        Map<String, Object> chatData = new HashMap<>();
        chatData.put("createdAt", FieldValue.serverTimestamp());
        chatData.put("title", messageText);

        chatsRef.add(chatData)
                .addOnSuccessListener(chatDocRef -> {
                    Map<String, Object> messageData = new HashMap<>();
                    messageData.put("text", messageText);
                    messageData.put("isUser", isUser);
                    messageData.put("timestamp", FieldValue.serverTimestamp());

                    chatDocRef.collection("messages")
                            .add(messageData)
                            .addOnSuccessListener(msgDoc -> callback.onSuccess(chatDocRef.getId()))
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public static void addMessageToChat(String chatId, String messageText, boolean isUser, FirestoreCallback<Void> callback) {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            callback.onFailure(new Exception("User not logged in"));
            return;
        }

        DocumentReference chatRef = db.collection("users")
                .document(user.getUid())
                .collection("chats")
                .document(chatId);

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("text", messageText);
        messageData.put("isUser", isUser);
        messageData.put("timestamp", FieldValue.serverTimestamp());

        chatRef.collection("messages")
                .add(messageData)
                .addOnSuccessListener(doc -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }

    public static void getMessagesForChat(String chatId, FirestoreCallback<List<Message>> callback) {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            callback.onFailure(new Exception("User not logged in"));
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Message> messages = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String text = doc.getString("text");
                        Boolean isUser = doc.getBoolean("isUser");

                        if (text != null && isUser != null) {
                            messages.add(new Message(text, isUser, false));
                        }
                    }
                    callback.onSuccess(messages);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public static void deleteChat(String chatId, FirestoreCallback<Void> callback) {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            callback.onFailure(new Exception("User not logged in"));
            return;
        }

        DocumentReference chatRef = db.collection("users")
                .document(user.getUid())
                .collection("chats")
                .document(chatId);

        // Delete messages inside the chat first
        chatRef.collection("messages")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        doc.getReference().delete();
                    }
                    // Then delete the chat document
                    chatRef.delete()
                            .addOnSuccessListener(unused -> callback.onSuccess(null))
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }
}
