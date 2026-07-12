package com.example.mappin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.mappin.data.PlaceRepository;
import com.example.mappin.data.TokenManager;
import com.example.mappin.model.Place;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Trang chủ - danh sách địa điểm đã lưu, có lọc theo danh mục.
 * Hiển thị cache (Room) trước để mở nhanh / xem offline, rồi tải server cập nhật.
 */
public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_PLACE = "extra_place";

    private PlaceAdapter adapter;
    private RecyclerView rvPlaces;
    private View emptyView;
    private View offlineBanner;
    private SwipeRefreshLayout swipeRefresh;
    private ChipGroup chipGroupFilter;

    private PlaceRepository repo;
    private final List<Place> allPlaces = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        repo = new PlaceRepository(this);

        rvPlaces = findViewById(R.id.rvPlaces);
        emptyView = findViewById(R.id.emptyView);
        offlineBanner = findViewById(R.id.offlineBanner);
        swipeRefresh = findViewById(R.id.swipeRefresh);
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

        // Kéo để làm mới (chỉ tải lại từ server)
        swipeRefresh.setOnRefreshListener(this::refreshFromServer);

        findViewById(R.id.fabAdd).setOnClickListener(v ->
                startActivity(new Intent(this, AddPlaceActivity.class)));

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            new TokenManager(this).clearTokens();
            repo.clearCache();   // xoá cache để không lẫn dữ liệu tài khoản khác
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPlaces();   // cache trước, rồi tải server (refresh mỗi khi quay lại)
    }

    private void loadPlaces() {
        String token = new TokenManager(this).getAccessToken();
        if (token == null) return;
        repo.load("Bearer " + token, this::onPlaces);
    }

    private void refreshFromServer() {
        String token = new TokenManager(this).getAccessToken();
        if (token == null) {
            swipeRefresh.setRefreshing(false);
            return;
        }
        repo.refresh("Bearer " + token, this::onPlaces);
    }

    // Kết quả từ repository (cache hoặc server, hoặc lỗi mạng)
    private void onPlaces(List<Place> places, boolean fromCache, boolean networkError) {
        if (networkError) {
            // Giữ nguyên danh sách đang hiển thị (cache), chỉ báo đang offline
            offlineBanner.setVisibility(View.VISIBLE);
            swipeRefresh.setRefreshing(false);
            return;
        }
        offlineBanner.setVisibility(View.GONE);
        allPlaces.clear();
        allPlaces.addAll(places);
        applyFilter();
        if (!fromCache) swipeRefresh.setRefreshing(false);   // đã có dữ liệu server mới
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
