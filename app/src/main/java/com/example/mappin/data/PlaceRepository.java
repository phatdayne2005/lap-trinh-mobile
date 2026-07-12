package com.example.mappin.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.mappin.data.local.AppDatabase;
import com.example.mappin.data.local.PlaceDao;
import com.example.mappin.data.local.PlaceEntity;
import com.example.mappin.model.Place;
import com.example.mappin.network.ApiClient;
import com.example.mappin.network.ApiService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Nguồn dữ liệu địa điểm: đọc cache (Room) trước cho hiển thị nhanh / xem offline,
 * rồi tải server để cập nhật. Chỉ hỗ trợ ĐỌC offline; thêm/sửa/xoá vẫn cần mạng.
 */
public class PlaceRepository {

    /**
     * @param places       danh sách địa điểm (null nếu là lần báo lỗi mạng)
     * @param fromCache     true = dữ liệu từ cache máy; false = vừa tải từ server
     * @param networkError  true = tải server thất bại/offline (giữ nguyên cache đang hiển thị)
     */
    public interface PlacesCallback {
        void onResult(List<Place> places, boolean fromCache, boolean networkError);
    }

    private final Context appCtx;
    private final PlaceDao dao;
    private final ApiService api;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public PlaceRepository(Context context) {
        this.appCtx = context.getApplicationContext();
        this.dao = AppDatabase.get(appCtx).placeDao();
        this.api = ApiClient.getApiService();
    }

    /** Trả cache ngay (nếu có) rồi tải server cập nhật. */
    public void load(String bearerToken, PlacesCallback cb) {
        io.execute(() -> {
            List<Place> cached = toPlaces(dao.getAll());
            main.post(() -> cb.onResult(cached, true, false));
        });
        refresh(bearerToken, cb);
    }

    /** Chỉ tải từ server; thành công thì thay toàn bộ cache (đồng bộ cả việc xoá). */
    public void refresh(String bearerToken, PlacesCallback cb) {
        if (bearerToken == null) {
            cb.onResult(null, false, true);
            return;
        }
        api.getPlaces(bearerToken).enqueue(new Callback<List<Place>>() {
            @Override
            public void onResponse(Call<List<Place>> call, Response<List<Place>> resp) {
                if (resp.isSuccessful() && resp.body() != null) {
                    List<Place> fresh = resp.body();
                    io.execute(() -> {
                        AppDatabase.get(appCtx).runInTransaction(() -> {
                            dao.clearAll();
                            dao.insertAll(toEntities(fresh));
                        });
                        main.post(() -> cb.onResult(fresh, false, false));
                    });
                } else {
                    cb.onResult(null, false, true);
                }
            }

            @Override
            public void onFailure(Call<List<Place>> call, Throwable t) {
                cb.onResult(null, false, true);   // mất mạng -> giữ cache
            }
        });
    }

    /** Xoá toàn bộ cache (gọi khi đăng nhập / đăng xuất để không lẫn dữ liệu tài khoản). */
    public void clearCache() {
        io.execute(dao::clearAll);
    }

    // ---------- chuyển đổi Entity <-> Place ----------
    private List<PlaceEntity> toEntities(List<Place> places) {
        List<PlaceEntity> list = new ArrayList<>();
        for (Place p : places) {
            PlaceEntity e = new PlaceEntity();
            e.id = p.getId() != null ? p.getId() : 0;
            e.placeName = p.getPlaceName();
            e.address = p.getAddress();
            e.category = p.getCategory();
            e.rating = p.getRating();
            e.note = p.getNote();
            e.mapUrl = p.getMapUrl();
            e.imageUrl = p.getImageUrl();
            e.createdAt = p.getCreatedAt();
            list.add(e);
        }
        return list;
    }

    private List<Place> toPlaces(List<PlaceEntity> entities) {
        List<Place> list = new ArrayList<>();
        for (PlaceEntity e : entities) {
            Place p = new Place(e.placeName, e.address, e.category, e.rating, e.note, e.mapUrl);
            p.setId(e.id);
            p.setImageUrl(e.imageUrl);
            p.setCreatedAt(e.createdAt);
            list.add(p);
        }
        return list;
    }
}
