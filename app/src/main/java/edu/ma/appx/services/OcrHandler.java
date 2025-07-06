package edu.ma.appx.services;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class OcrHandler {

    public interface OcrCallback {
        void onTextExtracted(String extractedText);
        void onError(String errorMessage);
    }

    public static final int REQUEST_CODE_CAMERA = 1001;
    public static final int REQUEST_CODE_FILE_PICK = 1002;

    private final Activity activity;
    private final OcrCallback callback;

    public OcrHandler(Activity activity, OcrCallback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public void startCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        activity.startActivityForResult(intent, REQUEST_CODE_CAMERA);
    }

    public void startFilePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        activity.startActivityForResult(intent, REQUEST_CODE_FILE_PICK);
    }

    public void handleActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            callback.onError("Operation canceled or failed.");
            return;
        }

        Bitmap bitmap = null;

        try {
            if (requestCode == REQUEST_CODE_CAMERA && data.getExtras() != null) {
                bitmap = (Bitmap) data.getExtras().get("data");

            } else if (requestCode == REQUEST_CODE_FILE_PICK) {
                Uri uri = data.getData();
                if (uri != null) {
                    bitmap = getBitmapFromUri(uri);
                }
            }

            if (bitmap != null) {
                processImage(bitmap);
            } else {
                callback.onError("Failed to get image.");
            }

        } catch (Exception e) {
            callback.onError("Error reading image: " + e.getMessage());
        }
    }

    private Bitmap getBitmapFromUri(Uri uri) throws Exception {
        ImageDecoder.Source source = ImageDecoder.createSource(activity.getContentResolver(), uri);
        return ImageDecoder.decodeBitmap(source);
    }

    private void processImage(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String extracted = visionText.getText().trim();
                    if (!extracted.isEmpty()) {
                        callback.onTextExtracted(extracted);
                    } else {
                        callback.onError("No text found.");
                    }
                })
                .addOnFailureListener(e -> callback.onError("Text recognition failed: " + e.getMessage()));
    }
}
