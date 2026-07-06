package com.example.mappin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mappin.data.TokenManager;
import com.example.mappin.model.AuthResponse;
import com.example.mappin.model.ErrorResponse;
import com.example.mappin.model.RegisterRequest;
import com.example.mappin.network.ApiClient;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Màn hình đăng ký (chỉ giao diện): email, họ tên, mật khẩu.
 * TODO: gọi API POST /auth/register rồi lưu token, chuyển sang trang chủ.
 */
public class RegisterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        findViewById(R.id.btnRegister).setOnClickListener(v -> {
            EditText etEmail = findViewById(R.id.etEmail);
            EditText etFullName = findViewById(R.id.etFullName);
            EditText etPassword = findViewById(R.id.etPassword);

            TextInputLayout tilEmail = findViewById(R.id.tilEmail);
            TextInputLayout tilFullName = findViewById(R.id.tilFullName);
            TextInputLayout tilPassword = findViewById(R.id.tilPassword);


            String email = etEmail.getText().toString().trim();
            String fullName = etFullName.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            tilEmail.setError(null);
            tilFullName.setError(null);
            tilPassword.setError(null);

            boolean valid = true;

            if (email.isEmpty()) {
                tilEmail.setError(getString(R.string.error_email_empty));
                valid = false;
            }

            if (fullName.isEmpty()){
                tilFullName.setError(getString(R.string.error_full_name_empty));
                valid = false;
            }

            if (password.isEmpty()){
                tilPassword.setError(getString(R.string.error_password_empty));
                valid = false;
            }

            if (!valid)
                return;

            RegisterRequest request = new RegisterRequest(email, fullName, password);

            ApiClient.getApiService().register(request).enqueue(new Callback<AuthResponse>() {
                @Override
                public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                    if (response.isSuccessful()) {
                        AuthResponse body = response.body();
                        Toast.makeText(RegisterActivity.this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show();
                        TokenManager tokenManager = new TokenManager(RegisterActivity.this);
                        tokenManager.saveTokens(body.getAccessToken(), body.getRefreshToken());
                        startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                        finish();
                    } else {
                        String message = "Đăng ký thất bại";
                        try {
                            if (response.errorBody() != null) {
                                String json = response.errorBody().string();
                                ErrorResponse err = new Gson().fromJson(json, ErrorResponse.class);
                                if (err != null && err.getDetail() != null){
                                    message = err.getDetail();
                                }
                            }
                        } catch (Exception e){
                            // Giữ message mặc định
                        }
                        Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<AuthResponse> call, Throwable throwable) {
                    Toast.makeText(RegisterActivity.this, "Lỗi kết nối: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Quay lại màn đăng nhập.
        findViewById(R.id.tvLogin).setOnClickListener(v -> finish());
    }
}
