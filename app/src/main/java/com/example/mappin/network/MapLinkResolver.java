package com.example.mappin.network;

import android.os.Handler;
import android.os.Looper;

import com.example.mappin.model.PlaceInfo;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class MapLinkResolver {
    public static PlaceInfo parse(String expandedUrl) {
        PlaceInfo info = new PlaceInfo();
        info.setMapUrl(expandedUrl);

        // Lấy đoạn giữa "/place/" và dấu "/" (hoặc "?") kế tiếp
        Matcher m = Pattern.compile("/place/([^/?]+)").matcher(expandedUrl);
        if (m.find()) {
            String raw = m.group(1);
            // URLDecoder tự đổi %XX -> ký tự, và '+' -> khoảng trắng
            String decoded;
            try {
                decoded = URLDecoder.decode(raw, "UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                decoded = raw.replace("+", " ");   // gần như không bao giờ xảy ra với UTF-8
            }

            // Tách theo dấu phẩy đầu tiên: trước là tên, sau là địa chỉ
            int comma = decoded.indexOf(',');
            if (comma > 0) {
                info.setName(decoded.substring(0, comma).trim());
                info.setAddress(decoded.substring(comma + 1).trim());
            } else {
                info.setName(decoded.trim());
            }
        }
        return info;
    }

    // Nơi trả kết quả về cho màn hình
    public interface ResolveCallback {
        void onResolved(PlaceInfo info);
        void onError(String message);
    }

    // Client dùng chung; BẬT follow redirect để tự đi hết chuỗi chuyển hướng
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    // Đưa kết quả về luồng chính (UI)
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void resolve(String shortUrl, ResolveCallback callback) {
        attempt(shortUrl, 3, callback);   // thử tối đa 3 lần
    }

    // Một lần thử mở link; nếu chưa lấy được thông tin thì tự gọi lại với triesLeft-1
    private static void attempt(String shortUrl, int triesLeft, ResolveCallback callback) {
        Request request = new Request.Builder()
                .url(shortUrl)
                .header("User-Agent", "Mozilla/5.0 (Android) Mappin")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (triesLeft > 1) {
                    attempt(shortUrl, triesLeft - 1, callback);
                    return;
                }
                mainHandler.post(() -> callback.onError("Lỗi mạng: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                // URL cuối cùng sau khi đi hết redirect. Bình thường là trang /place/...
                String finalUrl = response.request().url().toString();
                String candidate = finalUrl;

                // Google thỉnh thoảng trả thẳng 200 (trang interstitial) mà KHÔNG chuyển hướng
                // -> finalUrl vẫn là short link. Khi đó thử tìm URL /maps/place/ ngay trong HTML.
                if (!finalUrl.contains("/place/")) {
                    try {
                        if (response.body() != null) {
                            String body = response.body().string();
                            Matcher bm = Pattern.compile("https://www\\.google\\.com/maps/place/[^\"'\\\\ ]+").matcher(body);
                            if (bm.find()) candidate = bm.group();
                        }
                    } catch (Exception ignored) { /* bỏ qua, sẽ thử lại bên dưới */ }
                }
                response.close();

                PlaceInfo info = parse(candidate);

                // Vẫn chưa lấy được tên -> thử lại (đây chính là thao tác refocus thủ công trước đây)
                if (info.getName() == null && triesLeft > 1) {
                    attempt(shortUrl, triesLeft - 1, callback);
                    return;
                }
                mainHandler.post(() -> callback.onResolved(info));
            }
        });
    }

    public static boolean isGoogleMapUrl(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase();
        return u.startsWith("https://maps.app.goo.gl/")
                || u.startsWith("https://goo.gl/maps/")
                || u.startsWith("https://www.google.com/maps")
                || u.startsWith("https://maps.google.com/")
                || u.startsWith("http://maps.app.goo.gl/")
                || u.startsWith("http://www.google.com/maps")
                || u.startsWith("http://maps.google.com/");
    }
}
