package com.example.finix.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.ViewModelProvider;

import com.example.finix.MainActivity;                     // <-- Your dashboard
import com.example.finix.databinding.ActivityLoginBinding;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private LoginViewModel viewModel;
    private CardView cardLogin, cardSignup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);
        cardLogin = binding.cardLogin;
        cardSignup = binding.cardSignup;

        setupClicks();
        observeResult();
    }

    private void setupClicks() {
        binding.btnLogin.setOnClickListener(v -> {
            String email = binding.etEmail.getText().toString().trim();
            String pwd   = binding.etPassword.getText().toString();
            if (email.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.login(email, pwd);
        });

        binding.btnSignup.setOnClickListener(v -> {
            String name = binding.etUsername.getText().toString().trim();
            String email = binding.etSignupEmail.getText().toString().trim();
            String pwd   = binding.etSignupPassword.getText().toString();
            if (name.isEmpty() || email.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.signup(name, email, pwd);
        });

        binding.tvSignupLink.setOnClickListener(v -> showSignup());
        binding.tvLoginLink.setOnClickListener(v -> showLogin());
    }

    private void observeResult() {
        viewModel.getResult().observe(this, msg -> {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

            if (msg.startsWith("SUCCESS|")) {
                String username = msg.substring(8);  // Remove "SUCCESS|"
                goToDashboard(username);
            } else if (msg.contains("Signup successful")) {
                showLogin();
            }
        });
    }

    private void goToDashboard(String username) {
        Intent intent = new Intent(this, com.example.finix.MainActivity.class);
        intent.putExtra("username", username);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish(); // Prevents going back to login
    }

    private void showSignup() {
        cardLogin.setVisibility(CardView.GONE);
        cardSignup.setVisibility(CardView.VISIBLE);
    }

    private void showLogin() {
        cardSignup.setVisibility(CardView.GONE);
        cardLogin.setVisibility(CardView.VISIBLE);
    }
}