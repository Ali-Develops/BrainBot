package edu.ma.appx.services;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import java.util.ArrayList;
import java.util.Locale;

public class SpeechHelper {

    public interface SpeechCallback {
        void onSpeechResult(String result);
        void onError(String error);
    }

    private final Activity activity;
    private final SpeechCallback callback;

    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;

    private TextToSpeech textToSpeech;

    public SpeechHelper(Activity activity, SpeechCallback callback) {
        this.activity = activity;
        this.callback = callback;
        setupSpeechRecognizer();
        setupTextToSpeech();
    }

    private void setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(activity)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity);

            speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}

                @Override
                public void onError(int error) {
                    callback.onError("Speech error: " + error);
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        callback.onSpeechResult(matches.get(0));
                    } else {
                        callback.onError("No speech result.");
                    }
                }
            });
        } else {
            callback.onError("Speech recognition not available.");
        }
    }

    public void startListening() {
        if (speechRecognizer != null) {
            speechRecognizer.startListening(speechIntent);
        }
    }

    public void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(activity, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.getDefault());
            } else {
                callback.onError("Text-to-speech init failed.");
            }
        });
    }

    public void speakText(String text) {
        if (textToSpeech != null && !text.isEmpty()) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    public void stopSpeaking() {
        if (textToSpeech != null && textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }
    }

    public void shutdown() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (textToSpeech != null) textToSpeech.shutdown();
    }
}
