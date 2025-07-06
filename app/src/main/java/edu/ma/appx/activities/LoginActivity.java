package edu.ma.appx.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import edu.ma.appx.R;
import edu.ma.appx.services.FirebaseService;
import edu.ma.appx.utils.HelperFunctions;
import edu.ma.appx.utils.LoadingDialogUtil;

public class LoginActivity extends AppCompatActivity {

    EditText emailField, passwordField;
    Button loginBtn;
    TextView signupRedirect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailField = findViewById(R.id.emailInput);
        passwordField = findViewById(R.id.passwordInput);
        loginBtn = findViewById(R.id.loginBtn);
        signupRedirect = findViewById(R.id.signupRedirect);

        loginBtn.setOnClickListener(v -> {
            String email = emailField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show();
                return;
            }

            HelperFunctions.hideKeyboard(this, getCurrentFocus());
            LoadingDialogUtil.show(this);
            FirebaseService.loginUser(this, email, password, LoadingDialogUtil::dismiss);
        });

        signupRedirect.setOnClickListener(v -> {
            startActivity(new Intent(this, SignupActivity.class));
            finish();
        });
    }
}
