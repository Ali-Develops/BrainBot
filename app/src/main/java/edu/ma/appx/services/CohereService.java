package edu.ma.appx.services;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CohereService {

    private static final String TAG = "CohereService";
    private static final String API_URL = "https://api.cohere.ai/v1/generate";

    private final String apiKey;
    private final ExecutorService executor;

    public interface CohereCallback {
        void onSuccess(String responseText);
        void onError(String errorMessage);
    }

    public interface SuggestionsCallback {
        void onSuggestions(List<String> suggestions);
        void onError(String errorMessage);
    }

    public CohereService(String apiKey) {
        this.apiKey = apiKey;
        this.executor = Executors.newFixedThreadPool(2);
    }

    public void generateResponse(String prompt, CohereCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject jsonParam = new JSONObject();
                jsonParam.put("model", "command-r-plus");
                jsonParam.put("prompt", prompt);
                jsonParam.put("max_tokens", 300);
                jsonParam.put("temperature", 0.7);

                String reply = makeApiRequest(jsonParam);
                if (reply != null) {
                    callback.onSuccess(reply);
                } else {
                    callback.onError("No response from AI.");
                }

            } catch (JSONException e) {
                Log.e(TAG, "generateResponse JSON error: " + e.getMessage());
                callback.onError("Failed to build AI request.");
            }
        });
    }

    public void fetchSuggestions(String input, SuggestionsCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject jsonParam = new JSONObject();
                jsonParam.put("model", "command-r-plus");
                jsonParam.put("prompt", "Suggest 5 natural and concise question completions for: \"" + input + "\". List only the completions, plain text, one per line, no numbering. ");
                jsonParam.put("max_tokens", 100);
                jsonParam.put("temperature", 0.7);

                String result = makeApiRequest(jsonParam);
                if (result != null) {
                    List<String> suggestions = new ArrayList<>();
                    for (String line : result.split("\n")) {
                        String cleaned = line.trim().replaceAll("^\\d+\\.\\s*", "");
                        if (!cleaned.isEmpty()) suggestions.add(cleaned);
                    }
                    callback.onSuggestions(suggestions);
                } else {
                    callback.onSuggestions(new ArrayList<>());
                }

            } catch (JSONException e) {
                Log.e(TAG, "fetchSuggestions JSON error: " + e.getMessage());
                callback.onError("Failed to build suggestion request.");
            }
        });
    }

    private String makeApiRequest(JSONObject jsonParam) {
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonParam.toString().getBytes(StandardCharsets.UTF_8));
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            reader.close();

            JSONObject responseObject = new JSONObject(response.toString());
            JSONArray generations = responseObject.optJSONArray("generations");
            return generations != null && generations.length() > 0
                    ? generations.getJSONObject(0).getString("text").trim()
                    : null;

        } catch (Exception e) {
            Log.e(TAG, "API request failed: " + e.getMessage());
            return null;
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
