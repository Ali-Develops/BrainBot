package edu.ma.appx.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import edu.ma.appx.R;
import edu.ma.appx.services.*;
import edu.ma.appx.utils.HelperFunctions;
import edu.ma.appx.utils.LoadingDialogUtil;

public class SignupActivity extends AppCompatActivity {

    EditText emailField, passwordField, confirmPasswordField;
    Button signupBtn;
    TextView loginRedirect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        emailField = findViewById(R.id.emailInput);
        passwordField = findViewById(R.id.passwordInput);
        confirmPasswordField = findViewById(R.id.confirmPasswordInput);
        signupBtn = findViewById(R.id.signupBtn);
        loginRedirect = findViewById(R.id.loginRedirect);

        signupBtn.setOnClickListener(v -> {
            String email = emailField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();
            String confirm = confirmPasswordField.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(confirm)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            HelperFunctions.hideKeyboard(this, getCurrentFocus());
            LoadingDialogUtil.show(this);
            FirebaseService.signUpUser(this, email, password, LoadingDialogUtil::dismiss);
        });

        loginRedirect.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
}
