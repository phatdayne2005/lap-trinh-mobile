package com.example.mappin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mappin.data.TokenManager;
import com.example.mappin.model.Place;
import com.example.mappin.network.ApiClient;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Trang chủ - danh sách địa điểm đã lưu, có lọc theo danh mục.
 */
public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_PLACE = "extra_place";

    private PlaceAdapter adapter;
    private RecyclerView rvPlaces;
    private View emptyView;
    private ChipGroup chipGroupFilter;

    private final List<Place> allPlaces = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rvPlaces = findViewById(R.id.rvPlaces);
        emptyView = findViewById(R.id.emptyView);
        chipGroupFilter = findViewById(R.id.chipGroupFilter);

        adapter = new PlaceAdapter();
        rvPlaces.setLayoutManager(new LinearLayoutManager(this));
        rvPlaces.setAdapter(adapter);

        // Bấm card -> mở màn chi tiết, truyền cả object Place qua Intent
        adapter.setOnPlaceClickListener(place -> {
            Intent i = new Intent(this, PlaceDetailActivity.class);
            i.putExtra(EXTRA_PLACE, place);
            startActivity(i);
        });

        // Đổi chip lọc -> lọc lại danh sách
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilter());

        findViewById(R.id.fabAdd).setOnClickListener(v ->
                startActivity(new Intent(this, AddPlaceActivity.class)));

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            new TokenManager(this).clearTokens();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPlaces();   // refresh mỗi khi quay lại (sau khi thêm/sửa/xóa)
    }

    private void loadPlaces() {
        String token = new TokenManager(this).getAccessToken();
        if (token == null) return;

        ApiClient.getApiService().getPlaces("Bearer " + token).enqueue(new Callback<List<Place>>() {
            @Override
            public void onResponse(Call<List<Place>> call, Response<List<Place>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    allPlaces.clear();
                    allPlaces.addAll(response.body());
                    applyFilter();
                } else {
                    Toast.makeText(MainActivity.this, "Không tải được danh sách (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Place>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Lọc allPlaces theo chip đang chọn rồi đổ vào adapter
    private void applyFilter() {
        int checkedId = chipGroupFilter.getCheckedChipId();
        String category = null;               // chipAll -> null -> hiện tất cả
        if (checkedId == R.id.chipFilterFood) category = "an_uong";
        else if (checkedId == R.id.chipFilterHotel) category = "khach_san";
        else if (checkedId == R.id.chipFilterCafe) category = "ca_phe";
        else if (checkedId == R.id.chipFilterOther) category = "khac";

        List<Place> filtered = new ArrayList<>();
        for (Place p : allPlaces) {
            if (category == null || category.equals(p.getCategory())) {
                filtered.add(p);
            }
        }
        adapter.setPlaces(filtered);
        emptyView.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        rvPlaces.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
