package edu.ma.appx.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.navigation.NavigationView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import edu.ma.appx.R;
import edu.ma.appx.adapters.ChatAdapter;
import edu.ma.appx.adapters.HistoryAdapter;
import edu.ma.appx.adapters.SuggestedPromptsAdapter;
import edu.ma.appx.models.Message;
import edu.ma.appx.services.CohereService;
import edu.ma.appx.services.FirebaseService;
import edu.ma.appx.services.OcrHandler;
import edu.ma.appx.services.SpeechHelper;
import edu.ma.appx.utils.HelperFunctions;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 2;
    private boolean speak = false;

    private final Handler handler = new Handler();
    private Runnable suggestionRunnable;

    private RecyclerView chatRecyclerView;
    private EditText messageInput;
    private ChatAdapter chatAdapter;
    private List<Message> messageList;
    private LinearLayout welcomeLayout;

    private DrawerLayout drawerLayout;

    private final List<String> historyList = new ArrayList<>();
    private HistoryAdapter historyAdapterForNavHeader;

    private SuggestedPromptsAdapter suggestedPromptsAdapter;

    private String currentChatId = null;

    private CohereService cohereService;
    private OcrHandler ocrHandler;
    private SpeechHelper speechHelper;

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cohereService = new CohereService(getString(R.string.cohere_api_key));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayUseLogoEnabled(false);
            View customView = LayoutInflater.from(this).inflate(R.layout.toolbar_centered, toolbar, false);
            Toolbar.LayoutParams params = new Toolbar.LayoutParams(
                    Toolbar.LayoutParams.WRAP_CONTENT,
                    Toolbar.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER);
            customView.setLayoutParams(params);
            getSupportActionBar().setCustomView(customView);
            getSupportActionBar().setDisplayShowCustomEnabled(true);
        }

        welcomeLayout = findViewById(R.id.welcomeLayout);
        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        messageInput = findViewById(R.id.editTextMessage);
        ImageButton sendButton = findViewById(R.id.sendButton);
        ImageButton micButton = findViewById(R.id.micButton);
        ImageButton ocrButton = findViewById(R.id.ocrButton);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setAdapter(chatAdapter);

        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.navigation_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {}

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                HelperFunctions.hideKeyboard(MainActivity.this, getCurrentFocus());
                loadChatHistory();
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {}
            @Override
            public void onDrawerStateChanged(int newState) {}
        });

        View headerView = navigationView.getHeaderView(0);
        if (headerView != null) {
            ImageButton logoutButton = headerView.findViewById(R.id.logout);
            logoutButton.setOnClickListener(v -> {
                HelperFunctions.showConfirmationDialog(
                        MainActivity.this,
                        "Confirm Logout",
                        "Are you sure you want to logout?",
                        (dialogInterface, which) -> {
                            FirebaseService.logout();
                            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        }
                );
            });

            Button newChatButtonInNav = headerView.findViewById(R.id.newChatButton);
            RecyclerView historyRecyclerViewInNavHeader = headerView.findViewById(R.id.historyRecyclerView);

            if (historyRecyclerViewInNavHeader != null) {
                historyRecyclerViewInNavHeader.setLayoutManager(new LinearLayoutManager(this));
                historyAdapterForNavHeader = new HistoryAdapter(historyList, new HistoryAdapter.OnPromptClickListener() {
                    @Override
                    public void onPromptClick(String fullText) {
                        String[] parts = fullText.split("\n", 2);
                        if (parts.length < 2) return;

                        String chatId = parts[0].split(": ")[1];
                        currentChatId = chatId;

                        messageList.clear();
                        chatAdapter.notifyDataSetChanged();

                        FirebaseService.getMessagesForChat(chatId, new FirebaseService.FirestoreCallback<List<Message>>() {
                            @Override
                            public void onSuccess(List<Message> messages) {
                                runOnUiThread(() -> {
                                    messageList.addAll(messages);
                                    chatAdapter.notifyDataSetChanged();
                                    chatRecyclerView.scrollToPosition(messageList.size() - 1);
                                    welcomeLayout.setVisibility(View.GONE);
                                    drawerLayout.closeDrawer(GravityCompat.START);
                                });
                            }

                            @Override
                            public void onFailure(Exception e) {
                                runOnUiThread(() ->
                                        Toast.makeText(MainActivity.this, "Failed to load chat: " + e.getMessage(), Toast.LENGTH_LONG).show()
                                );
                            }
                        });
                    }

                    @Override
                    public void onPromptLongClick(String fullText) {
                        String[] parts = fullText.split("\n", 2);
                        if (parts.length < 2) return;

                        String chatId = parts[0].split(": ")[1];

                        HelperFunctions.showConfirmationDialog(
                                MainActivity.this,
                                "Delete Chat",
                                "Are you sure you want to delete this chat?",
                                (dialog, which) -> {
                                    FirebaseService.deleteChat(chatId, new FirebaseService.FirestoreCallback<Void>() {
                                        @Override
                                        public void onSuccess(Void result) {
                                            Toast.makeText(MainActivity.this, "Chat deleted", Toast.LENGTH_SHORT).show();

                                            if (chatId.equals(currentChatId)) {
                                                currentChatId = null;
                                                messageList.clear();
                                                chatAdapter.notifyDataSetChanged();
                                                welcomeLayout.setVisibility(View.VISIBLE);
                                            }

                                            loadChatHistory(); // Refresh history
                                        }

                                        @Override
                                        public void onFailure(Exception e) {
                                            Toast.makeText(MainActivity.this, "Failed to delete chat: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                        );
                    }
                });

                historyRecyclerViewInNavHeader.setAdapter(historyAdapterForNavHeader);
            }

            if (newChatButtonInNav != null) {
                newChatButtonInNav.setOnClickListener(v -> {
                    currentChatId = null;
                    messageList.clear();
                    chatAdapter.notifyDataSetChanged();
                    welcomeLayout.setVisibility(View.VISIBLE);
                    drawerLayout.closeDrawer(GravityCompat.START);
                });
            }
        }

        RecyclerView suggestedPromptsRecyclerView = findViewById(R.id.suggestedPromptsRecyclerView);

        suggestedPromptsRecyclerView.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        );

        suggestedPromptsAdapter = new SuggestedPromptsAdapter(new ArrayList<>(), prompt -> {
            messageInput.setText("");
            sendMessage(prompt);
            speak = false;
        });

        suggestedPromptsRecyclerView.setAdapter(suggestedPromptsAdapter);

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (suggestionRunnable != null) {
                    handler.removeCallbacks(suggestionRunnable); // cancel previous
                }

                final String input = s.toString();
                suggestionRunnable = () -> {
                    if (input.length() >= 3) {
                        fetchSuggestedPrompts(input);
                    } else {
                        suggestedPromptsAdapter.updatePrompts(new ArrayList<>());
                    }
                };

                handler.postDelayed(suggestionRunnable, 250); // delay for 250 milliseconds
            }

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }

        ocrButton.setOnClickListener(v -> {
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                    .withEndAction(() -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start())
                    .start();
            speechHelper.stopListening();
            speechHelper.stopSpeaking();
            showOcrOptionDialog();
        });

        micButton.setOnClickListener(v -> {
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                    .withEndAction(() -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start())
                    .start();
            speechHelper.stopListening();
            startVoiceInput();
        });

        sendButton.setOnClickListener(v -> {
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                    .withEndAction(() -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start())
                    .start();
            speechHelper.stopListening();
            speechHelper.stopSpeaking();
            speak = false;
            sendMessage(messageInput.getText().toString().trim());
        });

        ocrHandler = new OcrHandler(this, new OcrHandler.OcrCallback() {
            @Override
            public void onTextExtracted(String extractedText) {
                runOnUiThread(() -> {
                    sendMessage(extractedText);
                    speechHelper.stopSpeaking();
                    Toast.makeText(MainActivity.this, "Text extracted from image!", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show());
            }
        });

        speechHelper = new SpeechHelper(this, new SpeechHelper.SpeechCallback() {
            @Override
            public void onSpeechResult(String result) {
                speak = true;
                runOnUiThread(() -> sendMessage(result));
            }

            @Override
            public void onError(String error) {
                Log.d("Error", error);
            }
        });
    }

    private void loadChatHistory() {
        FirebaseService.getAllChats(new FirebaseService.FirestoreCallback<Map<String, String>>() {
            @Override
            public void onSuccess(Map<String, String> chatMap) {
                runOnUiThread(() -> {
                    historyList.clear();
                    historyList.addAll(chatMap.values()); // Add only titles if historyList is a List<String>

                    if (historyAdapterForNavHeader != null) {
                        List<String> displayList = new ArrayList<>();
                        for (Map.Entry<String, String> entry : chatMap.entrySet()) {
                            displayList.add("Chat: " + entry.getKey() + "\n" + entry.getValue());
                        }
                        historyAdapterForNavHeader.updateHistory(displayList);
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Failed to load history: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }


    private void fetchSuggestedPrompts(String input) {
        cohereService.fetchSuggestions(input, new CohereService.SuggestionsCallback() {
            @Override
            public void onSuggestions(List<String> suggestions) {
                runOnUiThread(() -> {
                    suggestedPromptsAdapter.updatePrompts(suggestions);
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    suggestedPromptsAdapter.updatePrompts(new ArrayList<>());
                });
            }
        });
    }

    private void startVoiceInput() {
        HelperFunctions.hideKeyboard(this, getCurrentFocus());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Mic permission not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...");

        speak = true;
        speechHelper.startListening();
    }

    private void showOcrOptionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose OCR Source");
        String[] options = {"Take Photo", "Select from Storage"};
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // Take Photo
                    ocrHandler.startCamera();
                    break;
                case 1: // Select from Storage
                    ocrHandler.startFilePicker();
                    break;
            }
        });
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ocrHandler.handleActivityResult(requestCode, resultCode, data);
    }

    private void sendMessage(String userText) {
        if (userText.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
            return;
        }

        welcomeLayout.setVisibility(View.GONE);

        // Add user message to list
        messageList.add(new Message(userText, true, false));
        messageList.add(new Message("Thinking...", false, false));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        chatRecyclerView.scrollToPosition(messageList.size() - 1);
        messageInput.setText("");

        if (currentChatId == null) {
            FirebaseService.createNewChat(userText, true, new FirebaseService.FirestoreCallback<String>() {
                @Override
                public void onSuccess(String newChatId) {
                    currentChatId = newChatId;
                    generateAIResponse(userText);
                }

                @Override
                public void onFailure(Exception e) {
                    Toast.makeText(MainActivity.this, "Failed to create chat: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } else {
            FirebaseService.addMessageToChat(currentChatId, userText, true, new FirebaseService.FirestoreCallback<Void>() {
                @Override public void onSuccess(Void unused) {}
                @Override public void onFailure(Exception e) {
                    Toast.makeText(MainActivity.this, "Failed to send message: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
            generateAIResponse(userText);
        }
    }

    private void generateAIResponse(String prompt) {
        cohereService.generateResponse(prompt, new CohereService.CohereCallback() {
            @Override
            public void onSuccess(String reply) {
                FirebaseService.addMessageToChat(currentChatId, reply, false, new FirebaseService.FirestoreCallback<Void>() {
                    @Override public void onSuccess(Void unused) {}
                    @Override public void onFailure(Exception e) {
                        Toast.makeText(MainActivity.this, "Failed to send message: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
                runOnUiThread(() -> {
                    if (!messageList.isEmpty()) {
                        int lastIndex = messageList.size() - 1;
                        messageList.remove(lastIndex);
                        chatAdapter.notifyItemRemoved(lastIndex);
                    }

                    messageList.add(new Message(reply, false, false));
                    chatAdapter.notifyItemInserted(messageList.size() - 1);
                    chatRecyclerView.scrollToPosition(messageList.size() - 1);

                    if (speak) {
                        speechHelper.speakText(reply);
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (speechHelper != null) {
            speechHelper.shutdown();
        }

        if (cohereService != null) cohereService.shutdown();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Audio recording permission granted.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Audio recording permission denied. Voice input will not work.", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Camera permission denied. Camera OCR will not work.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
