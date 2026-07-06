package com.example.mappin.network;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String BASE_URL = "https://mappin.phatnguyendev.site/";
    private static Retrofit retrofit;
    private static Retrofit plainRetrofit;

    public static ApiService getApiService() {
        if (retrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .authenticator(new AuthAuthenticator())   // tự refresh khi 401
                    .build();
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(ApiService.class);
    }

    // Ghép đường dẫn ảnh tương đối (vd /uploads/x.jpg) thành URL đầy đủ để tải bằng Glide
    public static String fullImageUrl(String path) {
        if (path == null || path.isEmpty()) return null;
        if (path.startsWith("http")) return path;
        return BASE_URL.replaceAll("/$", "") + path;
    }

    // Retrofit "trần" (KHÔNG authenticator) - dùng chính cho việc gọi refresh, tránh đệ quy
    static ApiService getPlainApiService() {
        if (plainRetrofit == null) {
            plainRetrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return plainRetrofit.create(ApiService.class);
    }
}
