package com.example.mappin;

import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

/**
 * Xem ảnh toàn cảnh trên nền đen. Chạm bất kỳ đâu để đóng.
 */
public class FullscreenImageActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URL = "extra_image_url";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_image);

        String url = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        ImageView iv = findViewById(R.id.ivFull);
        Glide.with(this).load(url).into(iv);

        findViewById(R.id.fullRoot).setOnClickListener(v -> finish());
    }
}
