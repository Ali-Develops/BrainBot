package edu.ma.appx;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService; // Added for threading management
import java.util.concurrent.Executors; // Added for threading management

// Missing imports for adapters and models (assuming these are in correct paths)
import edu.ma.appx.adapters.ChatAdapter;
import edu.ma.appx.adapters.HistoryAdapter;
import edu.ma.appx.adapters.SuggestedPromptsAdapter;
import edu.ma.appx.models.Message;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 2;
    private static final int REQUEST_IMAGE_CAPTURE = 3;
    private static final int REQUEST_PICK_IMAGE_OR_PDF = 4;

    private RecyclerView chatRecyclerView;
    private EditText messageInput;
    private ImageButton sendButton, micButton, ocrButton;
    private ChatAdapter chatAdapter;
    private List<Message> messageList;
    private ProgressBar loadingIndicator;
    private LinearLayout welcomeLayout;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private SharedPreferences prefs;
    private List<String> historyList = new ArrayList<>(); // For navigation drawer history
    private HistoryAdapter historyAdapterForNavHeader;
    private RecyclerView historyRecyclerViewInNavHeader;
    private View dimOverlay;

    private RecyclerView suggestedPromptsRecyclerView;
    private SuggestedPromptsAdapter suggestedPromptsAdapter;

    private Gson gson; // Gson instance for JSON serialization/deserialization

    // Added for threading management (optional, but good practice for network/long operations)
    private ExecutorService networkExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize ExecutorService
        networkExecutor = Executors.newFixedThreadPool(2); // Two threads for network ops (suggestions, AI response)

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayUseLogoEnabled(false);
            View customView = LayoutInflater.from(this).inflate(R.layout.toolbar_centered, null);
            Toolbar.LayoutParams params = new Toolbar.LayoutParams(
                    Toolbar.LayoutParams.WRAP_CONTENT,
                    Toolbar.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER);
            customView.setLayoutParams(params);
            getSupportActionBar().setCustomView(customView);
            getSupportActionBar().setDisplayShowCustomEnabled(true);
        }

        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        messageInput = findViewById(R.id.editTextMessage);
        sendButton = findViewById(R.id.sendButton);
        micButton = findViewById(R.id.micButton);
        ocrButton = findViewById(R.id.ocrButton);
        welcomeLayout = findViewById(R.id.welcomeLayout);
        dimOverlay = findViewById(R.id.dimOverlay);
        loadingIndicator = findViewById(R.id.loadingIndicator);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setAdapter(chatAdapter);

        prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE);
        gson = new Gson(); // Initialize Gson

        // Load existing chat history (full conversation and nav drawer history)
        loadChatHistory();

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        View headerView = navigationView.getHeaderView(0);
        if (headerView != null) {
            Button newChatButtonInNav = headerView.findViewById(R.id.newChatButton);
            Button btnClearHistoryInNav = headerView.findViewById(R.id.btnClearHistory);
            historyRecyclerViewInNavHeader = headerView.findViewById(R.id.historyRecyclerView);

            if (historyRecyclerViewInNavHeader != null) {
                historyRecyclerViewInNavHeader.setLayoutManager(new LinearLayoutManager(this));
                historyAdapterForNavHeader = new HistoryAdapter(historyList, prompt -> {
                    messageInput.setText(prompt);
                    drawerLayout.closeDrawer(GravityCompat.START);
                    // Optionally, start a new chat with the selected prompt
                    // To truly start a new chat, you'd clear messageList and then sendMessage(prompt)
                    // For now, it just populates the input.
                    // If you want to load a past chat:
                    // You'd need to save/load full conversations associated with these prompts.
                    // For example, each history item could be an ID to a saved conversation.
                    // For this refactor, it just fills the messageInput.
                });
                historyRecyclerViewInNavHeader.setAdapter(historyAdapterForNavHeader);
            }

            if (newChatButtonInNav != null) {
                newChatButtonInNav.setOnClickListener(v -> {
                    messageList.clear();
                    chatAdapter.notifyDataSetChanged();
                    saveFullChatHistory(); // Save empty chat
                    welcomeLayout.setVisibility(View.VISIBLE);
                    messageInput.setText("");
                    drawerLayout.closeDrawer(GravityCompat.START);
                    Toast.makeText(MainActivity.this, "New Chat Started", Toast.LENGTH_SHORT).show();
                });
            }

            if (btnClearHistoryInNav != null) {
                btnClearHistoryInNav.setOnClickListener(v -> {
                    // Clear both full chat and navigation drawer history
                    prefs.edit().remove("chat_history_ordered").apply();
                    prefs.edit().remove("full_chat_history").apply();

                    historyList.clear(); // Clear nav drawer history in memory
                    messageList.clear(); // Clear current chat display in memory

                    chatAdapter.notifyDataSetChanged(); // Update chat display
                    if (historyAdapterForNavHeader != null) {
                        historyAdapterForNavHeader.notifyDataSetChanged(); // Update nav drawer display
                    }

                    welcomeLayout.setVisibility(View.VISIBLE);
                    messageInput.setText("");
                    drawerLayout.closeDrawer(GravityCompat.START);
                    Toast.makeText(MainActivity.this, "History Cleared", Toast.LENGTH_SHORT).show();
                });
            }
        }

        navigationView.setNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.menu_history) {
                drawerLayout.openDrawer(Gravity.START);
            }
            return true;
        });

        suggestedPromptsRecyclerView = findViewById(R.id.suggestedPromptsRecyclerView);

        suggestedPromptsRecyclerView.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        );

        suggestedPromptsAdapter = new SuggestedPromptsAdapter(new ArrayList<>(), prompt -> {
            messageInput.setText(""); // Clear input box
            sendMessage(prompt);      // Auto-send the suggested prompt
        });

        suggestedPromptsRecyclerView.setAdapter(suggestedPromptsAdapter);

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() >= 3) {
                    fetchSuggestedPrompts(s.toString());
                } else {
                    // Clear suggested prompts if input is too short
                    suggestedPromptsAdapter.updatePrompts(new ArrayList<>());
                }
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

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle params) {}
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float rmsdB) {}
            public void onBufferReceived(byte[] buffer) {}
            public void onEndOfSpeech() {}
            public void onPartialResults(Bundle partialResults) {}
            public void onEvent(int eventType, Bundle params) {}
            public void onError(int error) {
                Toast.makeText(MainActivity.this, "Speech Error: " + error, Toast.LENGTH_SHORT).show();
                dimOverlay.setVisibility(View.GONE);
            }
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0);
                    messageInput.setText(text);
                    sendMessage(text); // Send the recognized text
                }
                dimOverlay.setVisibility(View.GONE);
            }
        });

        // Button click animations
        sendButton.setOnClickListener(v -> {
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                    .withEndAction(() -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start())
                    .start();
            sendMessage(messageInput.getText().toString().trim());
        });

        micButton.setOnClickListener(v -> {
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                    .withEndAction(() -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start())
                    .start();
            startVoiceInput();
        });

        ocrButton.setOnClickListener(v -> {
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                    .withEndAction(() -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start())
                    .start();
            showOcrOptionDialog();
        });
    }

    private void fetchSuggestedPrompts(String input) {
        networkExecutor.execute(() -> { // Use networkExecutor for this
            try {
                URL url = new URL("https://api.cohere.ai/v1/generate");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + getString(R.string.cohere_api_key));
                conn.setDoOutput(true);

                JSONObject requestBody = new JSONObject();
                requestBody.put("model", "command-r-plus");
                requestBody.put("prompt", "Suggest 5 helpful prompts related to: \"" + input + "\". Just list them in plain text separated by newlines.");
                requestBody.put("max_tokens", 100);
                requestBody.put("temperature", 0.7);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody.toString().getBytes("utf-8"));
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                reader.close(); // Close the reader

                JSONObject jsonResponse = new JSONObject(responseBuilder.toString());
                JSONArray generations = jsonResponse.optJSONArray("generations");
                String textResponse = generations != null && generations.length() > 0
                        ? generations.getJSONObject(0).getString("text").trim()
                        : "";

                List<String> suggestions = new ArrayList<>();
                for (String suggestion : textResponse.split("\n")) {
                    if (!suggestion.trim().isEmpty()) {
                        suggestions.add(suggestion.trim().replaceAll("^\\d+\\.\\s*", "")); // Remove numbering
                    }
                }

                runOnUiThread(() -> {
                    if (!suggestions.isEmpty() && suggestedPromptsAdapter != null) {
                        suggestedPromptsAdapter.updatePrompts(suggestions);
                    } else {
                        suggestedPromptsAdapter.updatePrompts(new ArrayList<>()); // Clear if no suggestions
                    }
                });

            } catch (IOException | JSONException e) { // Catch specific exceptions
                Log.e(TAG, "Error fetching suggestions: " + e.getMessage());
                runOnUiThread(() -> suggestedPromptsAdapter.updatePrompts(new ArrayList<>())); // Clear on error
            }
        });
    }

    private void startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Mic permission not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        dimOverlay.setVisibility(View.VISIBLE);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...");
        speechRecognizer.startListening(intent);
    }

    private void showOcrOptionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose OCR Source");
        String[] options = {"Take Photo", "Select from Storage"};
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // Take Photo
                    startCameraOCR();
                    break;
                case 1: // Select from Storage
                    startFilePickerOCR();
                    break;
            }
        });
        builder.show();
    }

    private void startCameraOCR() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            runOnUiThread(() -> loadingIndicator.setVisibility(View.VISIBLE)); // Show loading as soon as capture starts
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void startFilePickerOCR() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*|application/pdf");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select Image or PDF"), REQUEST_PICK_IMAGE_OR_PDF);
        runOnUiThread(() -> loadingIndicator.setVisibility(View.VISIBLE)); // Show loading as soon as picker starts
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                Bundle extras = data.getExtras();
                Bitmap imageBitmap = (Bitmap) extras.get("data");
                if (imageBitmap != null) {
                    recognizeTextFromImage(imageBitmap);
                } else {
                    // Bitmap is null, hide indicator
                    runOnUiThread(() -> loadingIndicator.setVisibility(View.GONE));
                    Toast.makeText(this, "Failed to capture image.", Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == REQUEST_PICK_IMAGE_OR_PDF) {
                Uri selectedFileUri = data.getData();
                if (selectedFileUri != null) {
                    String mimeType = getContentResolver().getType(selectedFileUri);
                    if (mimeType != null && mimeType.startsWith("image/")) {
                        try {
                            Bitmap selectedImageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedFileUri);
                            recognizeTextFromImage(selectedImageBitmap);
                        } catch (IOException e) {
                            Log.e(TAG, "Error loading image from URI: " + e.getMessage());
                            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                            runOnUiThread(() -> loadingIndicator.setVisibility(View.GONE));
                        }
                    } else if (mimeType != null && mimeType.equals("application/pdf")) {
                        recognizeTextFromPdf(selectedFileUri);
                    } else {
                        Toast.makeText(this, "Unsupported file type selected", Toast.LENGTH_SHORT).show();
                        runOnUiThread(() -> loadingIndicator.setVisibility(View.GONE));
                    }
                } else {
                    // No URI selected, hide indicator
                    runOnUiThread(() -> loadingIndicator.setVisibility(View.GONE));
                }
            }
        } else {
            // User cancelled picking/capture, ensure loading indicator is hidden
            runOnUiThread(() -> loadingIndicator.setVisibility(View.GONE));
            Toast.makeText(this, "Image/PDF selection cancelled.", Toast.LENGTH_SHORT).show();
        }
    }

    private void recognizeTextFromImage(Bitmap bitmap) {
        if (bitmap == null) {
            Toast.makeText(this, "Error: Bitmap is null for OCR.", Toast.LENGTH_SHORT).show();
            runOnUiThread(() -> loadingIndicator.setVisibility(View.GONE));
            return;
        }

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // Loading indicator should already be visible from startCameraOCR/startFilePickerOCR

        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String recognizedText = visionText.getText();
                    runOnUiThread(() -> {
                        if (!recognizedText.isEmpty()) {
                            messageInput.setText(recognizedText);
                            sendMessage(recognizedText); // Send the recognized text
                        } else {
                            Toast.makeText(MainActivity.this, "No text found in image", Toast.LENGTH_SHORT).show();
                        }
                        loadingIndicator.setVisibility(View.GONE);
                    });
                })
                .addOnFailureListener(e -> {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "OCR failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        Log.e(TAG, "OCR failed: " + e.getMessage());
                        loadingIndicator.setVisibility(View.GONE);
                    });
                });
    }

    private void recognizeTextFromPdf(Uri pdfUri) {
        // Loading indicator should already be visible from startFilePickerOCR
        networkExecutor.execute(() -> { // Use networkExecutor for PDF processing too
            ParcelFileDescriptor pfd = null;
            PdfRenderer renderer = null;
            PdfRenderer.Page page = null;
            Bitmap bitmap = null;
            try {
                pfd = getContentResolver().openFileDescriptor(pdfUri, "r");
                if (pfd == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to open PDF file.", Toast.LENGTH_SHORT).show();
                        loadingIndicator.setVisibility(View.GONE);
                    });
                    return;
                }

                renderer = new PdfRenderer(pfd);
                if (renderer.getPageCount() == 0) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "PDF has no pages.", Toast.LENGTH_SHORT).show();
                        loadingIndicator.setVisibility(View.GONE);
                    });
                    return;
                }

                // Use a fixed higher scale factor for potentially better OCR results
                float scaleFactor = 4.0f; // Targeting 4x resolution for OCR

                page = renderer.openPage(0); // Process the first page

                int width = (int) (page.getWidth() * scaleFactor);
                int height = (int) (page.getHeight() * scaleFactor);

                // Ensure bitmap dimensions are positive to avoid IllegalArgumentException
                if (width <= 0 || height <= 0) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to render PDF page: Invalid dimensions.", Toast.LENGTH_SHORT).show();
                        loadingIndicator.setVisibility(View.GONE);
                    });
                    return;
                }

                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                Bitmap finalBitmap = bitmap; // Need a final reference for the Runnable
                runOnUiThread(() -> recognizeTextFromImage(finalBitmap)); // Pass to image recognizer

            } catch (IOException e) {
                Log.e(TAG, "Error processing PDF: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Error processing PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    loadingIndicator.setVisibility(View.GONE);
                });
            } finally {
                // Ensure all resources are closed
                if (page != null) {
                    try {
                        page.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing PDF page: " + e.getMessage());
                    }
                }
                if (renderer != null) {
                    try {
                        renderer.close();
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing PDF renderer: " + e.getMessage());
                    }
                }
                if (pfd != null) {
                    try {
                        pfd.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing ParcelFileDescriptor: " + e.getMessage());
                    }
                }
                // loadingIndicator.setVisibility(View.GONE) is handled by recognizeTextFromImage's success/failure listeners
            }
        });
    }

    private void sendMessage(String userText) {
        if (userText.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show();
            return;
        }

        welcomeLayout.setVisibility(View.GONE);
        loadingIndicator.setVisibility(View.VISIBLE);

        // Add user message to list
        messageList.add(new Message(userText, true, false));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        chatRecyclerView.scrollToPosition(messageList.size() - 1);
        messageInput.setText("");

        // Save the full chat conversation after user message
        saveFullChatHistory();
        // Add user's prompt to navigation drawer history (managing duplicates internally)
        addPromptToNavHistory(userText);

        generateAIResponse(userText);
    }

    private void generateAIResponse(String prompt) {
        networkExecutor.execute(() -> { // Use networkExecutor for this
            try {
                URL url = new URL("https://api.cohere.ai/v1/generate");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + getString(R.string.cohere_api_key));
                conn.setDoOutput(true);

                JSONObject jsonParam = new JSONObject();
                jsonParam.put("model", "command-r-plus");
                jsonParam.put("prompt", prompt);
                jsonParam.put("max_tokens", 300);
                jsonParam.put("temperature", 0.7);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonParam.toString().getBytes("utf-8"));
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close(); // Close the reader

                JSONObject responseObject = new JSONObject(response.toString());
                JSONArray generations = responseObject.optJSONArray("generations");
                String reply = generations != null && generations.length() > 0
                        ? generations.getJSONObject(0).getString("text").trim()
                        : "No response from AI.";

                runOnUiThread(() -> {
                    // Add AI message to list
                    messageList.add(new Message(reply, false, false));
                    chatAdapter.notifyItemInserted(messageList.size() - 1);
                    chatRecyclerView.scrollToPosition(messageList.size() - 1);
                    textToSpeech.speak(reply, TextToSpeech.QUEUE_FLUSH, null, null);
                    loadingIndicator.setVisibility(View.GONE);

                    // Save the full chat again after AI response (now includes both sides)
                    saveFullChatHistory();
                });

            } catch (IOException | JSONException e) { // Catch specific exceptions
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Error fetching AI response: " + e.getMessage(), Toast.LENGTH_LONG).show());
                runOnUiThread(() -> loadingIndicator.setVisibility(View.GONE));
                Log.e(TAG, "Error fetching AI response: ", e);
            }
        });
    }

    // New method to save the full chat conversation
    private void saveFullChatHistory() {
        String jsonMessages = gson.toJson(messageList);
        prefs.edit().putString("full_chat_history", jsonMessages).apply();
    }

    // New method to manage and save navigation drawer history
    private void addPromptToNavHistory(String promptToAdd) {
        // Ensure the prompt is not empty and is a reasonable length for history
        if (promptToAdd.trim().isEmpty()) {
            return;
        }

        // Remove if the prompt already exists to bring it to the top
        historyList.remove(promptToAdd);

        // Add the new prompt to the beginning of the list
        historyList.add(0, promptToAdd);

        // Optionally, limit the number of items in the history list
        int maxHistoryItems = 20; // You can adjust this number
        if (historyList.size() > maxHistoryItems) {
            // Keep only the most recent items
            historyList = new ArrayList<>(historyList.subList(0, maxHistoryItems));
        }

        // Notify the adapter that its data has changed
        if (historyAdapterForNavHeader != null) {
            historyAdapterForNavHeader.updateHistory(historyList); // Assuming an update method in adapter
            // Or if your adapter directly uses historyList, you might need:
            // historyAdapterForNavHeader.notifyDataSetChanged();
        }

        // Save the updated navigation drawer history to SharedPreferences
        JSONArray navHistoryArray = new JSONArray(historyList);
        prefs.edit().putString("chat_history_ordered", navHistoryArray.toString()).apply();
    }


    // Method to load full chat history and navigation history
    private void loadChatHistory() {
        // Load full chat history (for chat screen)
        String jsonMessages = prefs.getString("full_chat_history", null);
        if (jsonMessages != null) {
            Type type = new TypeToken<ArrayList<Message>>() {}.getType();
            List<Message> loadedList = gson.fromJson(jsonMessages, type);
            if (loadedList != null && !loadedList.isEmpty()) {
                messageList.clear();
                messageList.addAll(loadedList);
                chatAdapter.notifyDataSetChanged();
                welcomeLayout.setVisibility(View.GONE);
                chatRecyclerView.scrollToPosition(messageList.size() - 1);
            }
        }

        // Load navigation drawer history (for sidebar)
        String jsonNavHistory = prefs.getString("chat_history_ordered", "[]");
        try {
            JSONArray array = new JSONArray(jsonNavHistory);
            historyList.clear();
            for (int i = 0; i < array.length(); i++) {
                historyList.add(array.getString(i));
            }
            // Ensure the nav history adapter is notified if data loaded after its creation
            if (historyAdapterForNavHeader != null) {
                historyAdapterForNavHeader.updateHistory(historyList); // Or notifyDataSetChanged()
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading navigation history: " + e.getMessage());
            // If parsing fails, clear it to avoid stale data
            historyList.clear();
            if (historyAdapterForNavHeader != null) {
                historyAdapterForNavHeader.updateHistory(historyList);
            }
        }
    }


    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        // Shut down the executor service to prevent memory leaks
        if (networkExecutor != null) {
            networkExecutor.shutdownNow(); // Attempts to stop all actively executing tasks
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
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