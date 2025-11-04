package com.example.finix.ui.login;

import android.app.Application;
import android.util.Patterns;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.finix.data.FinixDatabase;
import com.example.finix.data.User;
import org.mindrot.jbcrypt.BCrypt;

public class LoginViewModel extends AndroidViewModel {

    private final MutableLiveData<String> result = new MutableLiveData<>();

    public LoginViewModel(Application application) {
        super(application);
    }

    public void login(String email, String password) {
        if (!isValidGmail(email)) {
            result.postValue("Please use a valid @gmail.com email");
            return;
        }

        new Thread(() -> {
            try {
                User user = FinixDatabase.getDatabase(getApplication())
                        .userDao()
                        .getUserByEmail(email);

                if (user != null && BCrypt.checkpw(password, user.passwordHash)) {
                    result.postValue("SUCCESS|" + user.username);  // ← USE "SUCCESS|"
                } else {
                    result.postValue("Invalid email or password");
                }
            } catch (Exception e) {
                result.postValue("Login error. Try again.");
            }
        }).start();
    }

    public void signup(String username, String email, String password) {
        // ---- UI validation ----
        if (username == null || username.trim().isEmpty()) {
            result.postValue("Username is required");
            return;
        }
        if (!isValidGmail(email)) {
            result.postValue("Email must be a valid @gmail.com address");
            return;
        }
        if (password == null || password.length() < 6) {
            result.postValue("Password must be at least 6 characters");
            return;
        }

        new Thread(() -> {
            try {
                // Extra safety check (DB will also enforce)
                User existing = FinixDatabase.getDatabase(getApplication())
                        .userDao()
                        .getUserByEmail(email);
                if (existing != null) {
                    result.postValue("This @gmail.com address is already registered");
                    return;
                }

                String hash = BCrypt.hashpw(password, BCrypt.gensalt());
                User newUser = new User(username.trim(), email.trim(), hash);
                FinixDatabase.getDatabase(getApplication()).userDao().insert(newUser);

                result.postValue("Signup successful! Please login.");
            } catch (Exception e) {
                // Catches SQLiteConstraintException (duplicate email)
                result.postValue("Email already exists. Try another.");
            }
        }).start();
    }

    private boolean isValidGmail(String email) {
        return email != null
                && Patterns.EMAIL_ADDRESS.matcher(email).matches()
                && email.toLowerCase().endsWith("@gmail.com");
    }

    public MutableLiveData<String> getResult() {
        return result;
    }
}