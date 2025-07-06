package edu.ma.appx.utils;
import android.app.Activity;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import edu.ma.appx.R;

public class LoadingDialogUtil {

    private static AlertDialog dialog;

    public static void show(Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_loading, null);
        builder.setView(view);
        builder.setCancelable(false);
        dialog = builder.create();
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.6),
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
        }
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    public static void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
