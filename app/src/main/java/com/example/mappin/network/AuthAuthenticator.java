package com.example.mappin.network;

import com.example.mappin.MappinApp;
import com.example.mappin.data.TokenManager;
import com.example.mappin.model.AuthResponse;
import com.example.mappin.model.RefreshRequest;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class AuthAuthenticator implements Authenticator {

    @Override
    public Request authenticate(Route route, Response response) throws IOException {
        // Không refresh cho chính các endpoint /auth/ (login/register/refresh)
        if (response.request().url().encodedPath().contains("/auth/")) return null;

        // Đã thử lại 1 lần mà vẫn 401 -> bỏ cuộc, tránh lặp vô hạn
        if (responseCount(response) >= 2) return null;

        TokenManager tokenManager = new TokenManager(MappinApp.getAppContext());
        String refreshToken = tokenManager.getRefreshToken();
        if (refreshToken == null) return null;

        // Gọi /auth/refresh ĐỒNG BỘ (đang ở luồng nền của OkHttp)
        AuthResponse newTokens = null;
        try {
            retrofit2.Response<AuthResponse> resp = ApiClient.getPlainApiService()
                    .refreshToken(new RefreshRequest(refreshToken)).execute();
            if (resp.isSuccessful()) newTokens = resp.body();
        } catch (IOException ignored) { }

        if (newTokens == null) {
            tokenManager.clearTokens();   // refresh cũng hỏng -> buộc đăng nhập lại
            return null;
        }

        // Server XOAY VÒNG refresh token -> phải lưu cả cặp mới
        tokenManager.saveTokens(newTokens.getAccessToken(), newTokens.getRefreshToken());

        // Trả về request cũ kèm access token mới -> OkHttp tự thử lại
        return response.request().newBuilder()
                .header("Authorization", "Bearer " + newTokens.getAccessToken())
                .build();
    }

    // Đếm số lần response đã bị thử (qua chuỗi priorResponse)
    private int responseCount(Response response) {
        int count = 1;
        while ((response = response.priorResponse()) != null) count++;
        return count;
    }
}
