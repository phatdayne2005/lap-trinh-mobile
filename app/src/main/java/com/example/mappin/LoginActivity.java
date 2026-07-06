package com.example.mappin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mappin.data.TokenManager;
import com.example.mappin.model.AuthResponse;
import com.example.mappin.model.ErrorResponse;
import com.example.mappin.model.LoginRequest;
import com.example.mappin.network.ApiClient;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Màn hình đăng nhập (chỉ giao diện).
 * TODO: xử lý đăng nhập/đăng ký thật ở đây.
 */
public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TokenManager tokenManager = new TokenManager(this);
        if (tokenManager.isLoggedIn()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_login);

        findViewById(R.id.btnLogin).setOnClickListener(v -> {
            EditText etEmail = findViewById(R.id.etEmail);
            EditText etPassword = findViewById(R.id.etPassword);
            TextInputLayout tilEmail = findViewById(R.id.tilEmail);
            TextInputLayout tilPassword = findViewById(R.id.tilPassword);

            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            tilEmail.setError(null);
            tilPassword.setError(null);

            boolean valid = true;

            if (email.isEmpty()) {
                tilEmail.setError(getString(R.string.error_email_empty));
                valid = false;
            }

            if (password.isEmpty()) {
                tilPassword.setError(getString(R.string.error_password_empty));
                valid = false;
            }
            if (!valid)
                return;

            LoginRequest request = new LoginRequest(email, password);
            ApiClient.getApiService().login(request).enqueue(new Callback<AuthResponse>() {
                @Override
                public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                    if (response.isSuccessful()) {
                        AuthResponse body = response.body();
                        Toast.makeText(LoginActivity.this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                        TokenManager tokenManager = new TokenManager(LoginActivity.this);
                        tokenManager.saveTokens(body.getAccessToken(), body.getRefreshToken());
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        String message = "Đăng nhập thất bại";
                        try {
                            if (response.errorBody() != null) {
                                String json = response.errorBody().string();
                                ErrorResponse err = new Gson().fromJson(json, ErrorResponse.class);
                                if (err != null && err.getDetail() != null) {
                                    message = err.getDetail();
                                }
                            }
                        } catch (Exception e) {
                            // Giữ message mặc định
                        }
                        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<AuthResponse> call, Throwable t) {
                    Toast.makeText(LoginActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        findViewById(R.id.tvRegister).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }
}
