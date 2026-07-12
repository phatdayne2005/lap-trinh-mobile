package com.example.mappin;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.mappin.data.TokenManager;
import com.example.mappin.model.Place;
import com.example.mappin.network.ApiClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Màn xem chi tiết 1 địa điểm: hiển thị thông tin + mở bản đồ / sửa / xóa.
 */
public class PlaceDetailActivity extends AppCompatActivity {

    private Place place;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place_detail);

        place = (Place) getIntent().getSerializableExtra(MainActivity.EXTRA_PLACE);
        if (place == null) {
            finish();
            return;
        }

        bind();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnOpenMap).setOnClickListener(v -> openMap());
        findViewById(R.id.btnEdit).setOnClickListener(v -> {
            Intent i = new Intent(this, AddPlaceActivity.class);
            i.putExtra(AddPlaceActivity.EXTRA_EDIT_PLACE, place);
            startActivity(i);
        });
        findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDelete());
    }

    private void bind() {
        ((TextView) findViewById(R.id.tvDetailName)).setText(place.getPlaceName());
        ((TextView) findViewById(R.id.tvDetailCategory)).setText(categoryLabel(place.getCategory()));

        TextView tvRating = findViewById(R.id.tvDetailRating);
        if (place.getRating() != null) {
            tvRating.setText("★ " + place.getRating());
            tvRating.setVisibility(View.VISIBLE);
        } else {
            tvRating.setVisibility(View.GONE);
        }

        // Địa chỉ (ẩn nếu trống)
        if (place.getAddress() != null && !place.getAddress().isEmpty()) {
            ((TextView) findViewById(R.id.tvDetailAddress))
                    .setText(com.example.mappin.util.AddressUtils.stripPostalCode(place.getAddress()));
        } else {
            findViewById(R.id.addressBlock).setVisibility(View.GONE);
        }

        // Ghi chú (ẩn nếu trống)
        if (place.getNote() != null && !place.getNote().isEmpty()) {
            ((TextView) findViewById(R.id.tvDetailNote)).setText(place.getNote());
        } else {
            findViewById(R.id.noteBlock).setVisibility(View.GONE);
        }

        // Ảnh (nếu có) - bấm vào để xem toàn cảnh
        String imageUrl = ApiClient.fullImageUrl(place.getImageUrl());
        if (imageUrl != null) {
            findViewById(R.id.photoCard).setVisibility(View.VISIBLE);
            ImageView iv = findViewById(R.id.ivDetailPhoto);
            Glide.with(this).load(imageUrl).into(iv);
            iv.setOnClickListener(v -> {
                Intent i = new Intent(this, FullscreenImageActivity.class);
                i.putExtra(FullscreenImageActivity.EXTRA_IMAGE_URL, imageUrl);
                startActivity(i);
            });
        }
    }

    // Mở bản đồ, ưu tiên nhảy THẲNG vào app Google Maps
    private void openMap() {
        String mapUrl = place.getMapUrl();
        Uri uri;
        if (mapUrl != null && mapUrl.contains("google.com/maps")) {
            // Link đã ở dạng đầy đủ -> mở đúng địa điểm
            uri = Uri.parse(mapUrl);
        } else {
            // Short link maps.app.goo.gl KHÔNG mở thẳng app được -> dùng tên + địa chỉ qua geo:
            String query = buildMapQuery();
            if (query != null) {
                uri = Uri.parse("geo:0,0?q=" + Uri.encode(query));
            } else if (mapUrl != null && !mapUrl.isEmpty()) {
                uri = Uri.parse(mapUrl);   // cùng lắm mở short link (qua trình duyệt)
            } else {
                Toast.makeText(this, "Địa điểm này chưa có vị trí để mở bản đồ", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        openInMaps(uri);
    }

    // Ghép "Tên, Địa chỉ" làm từ khóa tìm trên bản đồ
    private String buildMapQuery() {
        StringBuilder sb = new StringBuilder();
        String name = place.getPlaceName();
        String address = place.getAddress();
        if (name != null && !name.isEmpty()) sb.append(name);
        if (address != null && !address.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(address);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private void openInMaps(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.setPackage("com.google.android.apps.maps");   // ép vào Google Maps, không hiện hộp chọn
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            // Chưa cài Google Maps -> mở bằng app bất kỳ (trình duyệt)
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(this, "Không tìm thấy ứng dụng bản đồ", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa địa điểm")
                .setMessage("Bạn chắc chắn muốn xóa \"" + place.getPlaceName() + "\"?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> doDelete())
                .show();
    }

    private void doDelete() {
        String token = new TokenManager(this).getAccessToken();
        if (token == null) {
            Toast.makeText(this, "Phiên đăng nhập đã hết", Toast.LENGTH_SHORT).show();
            return;
        }
        ApiClient.getApiService().deletePlace("Bearer " + token, place.getId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(PlaceDetailActivity.this, "Đã xóa", Toast.LENGTH_SHORT).show();
                    finish();   // về Main, onResume sẽ tải lại danh sách
                } else {
                    Toast.makeText(PlaceDetailActivity.this, "Xóa thất bại (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(PlaceDetailActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String categoryLabel(String code) {
        if (code == null) return "📍 Khác";
        switch (code) {
            case "an_uong":   return "🍜 Ăn uống";
            case "khach_san": return "🏨 Khách sạn";
            case "ca_phe":    return "☕ Cà phê";
            default:          return "📍 Khác";
        }
    }
}
