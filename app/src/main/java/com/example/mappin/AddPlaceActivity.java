package com.example.mappin;

import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.example.mappin.data.TokenManager;
import com.example.mappin.model.ErrorResponse;
import com.example.mappin.model.Place;
import com.example.mappin.model.PlaceInfo;
import com.example.mappin.network.ApiClient;
import com.example.mappin.network.MapLinkResolver;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Màn thêm/sửa địa điểm. Nếu nhận EXTRA_EDIT_PLACE -> chế độ sửa.
 * Hỗ trợ dán/chụp/chọn ảnh và link Google Maps.
 */
public class AddPlaceActivity extends AppCompatActivity {

    public static final String EXTRA_EDIT_PLACE = "extra_edit_place";

    private EditText etMapUrl, etName, etAddress, etNote;
    private TextInputLayout tilMapUrl;
    private ChipGroup chipGroup;
    private RatingBar ratingBar;
    private ImageView ivPhoto;
    private View photoPlaceholder;

    private Place editingPlace;      // null = thêm mới
    private Uri pendingPhotoUri;     // ảnh vừa chọn/chụp, chờ upload
    private Uri cameraImageUri;      // uri tạm cho camera

    // Chọn ảnh từ thư viện
    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) showPhoto(uri);
            });

    // Chụp ảnh bằng camera
    private final ActivityResultLauncher<Uri> takePhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraImageUri != null) showPhoto(cameraImageUri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_place);

        etMapUrl = findViewById(R.id.etMapUrl);
        etName = findViewById(R.id.etName);
        etAddress = findViewById(R.id.etAddress);
        etNote = findViewById(R.id.etNote);
        tilMapUrl = findViewById(R.id.tilMapUrl);
        chipGroup = findViewById(R.id.chipGroupCategory);
        ratingBar = findViewById(R.id.ratingBar);
        ivPhoto = findViewById(R.id.ivPhoto);
        photoPlaceholder = findViewById(R.id.photoPlaceholder);

        // Chế độ sửa?
        editingPlace = (Place) getIntent().getSerializableExtra(EXTRA_EDIT_PLACE);
        if (editingPlace != null) {
            ((TextView) findViewById(R.id.tvToolbarTitle)).setText("Sửa địa điểm");
            prefillForEdit();
        }

        // Nhận link chia sẻ từ app khác (Google Maps -> Share -> Mappin)
        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null) {
                String url = extractUrl(shared);
                if (url != null) {
                    etMapUrl.setText(url);
                    resolveMapLink(url);
                }
            }
        }

        etMapUrl.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) resolveMapLink(etMapUrl.getText().toString());
        });

        tilMapUrl.setEndIconOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()
                    && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence pasted = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
                String text = (pasted == null) ? "" : pasted.toString().trim();
                etMapUrl.setText(text);
                if (!text.isEmpty()) resolveMapLink(text);
                else Toast.makeText(this, "Clipboard trống", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Chọn/chụp ảnh
        findViewById(R.id.photoContainer).setOnClickListener(v -> showPhotoOptions());

        findViewById(R.id.btnSave).setOnClickListener(v -> save());
    }

    // ---------- Prefill khi sửa ----------
    private void prefillForEdit() {
        etName.setText(editingPlace.getPlaceName());
        etAddress.setText(editingPlace.getAddress());
        etNote.setText(editingPlace.getNote());
        etMapUrl.setText(editingPlace.getMapUrl());
        ratingBar.setRating(editingPlace.getRating() != null ? editingPlace.getRating() : 0);

        String cat = editingPlace.getCategory();
        if ("khach_san".equals(cat)) chipGroup.check(R.id.chipHotel);
        else if ("ca_phe".equals(cat)) chipGroup.check(R.id.chipCafe);
        else if ("khac".equals(cat)) chipGroup.check(R.id.chipOther);
        else chipGroup.check(R.id.chipFood);

        String imageUrl = ApiClient.fullImageUrl(editingPlace.getImageUrl());
        if (imageUrl != null) {
            ivPhoto.setVisibility(View.VISIBLE);
            photoPlaceholder.setVisibility(View.GONE);
            Glide.with(this).load(imageUrl).into(ivPhoto);
        }
    }

    // ---------- Ảnh ----------
    private void showPhotoOptions() {
        String[] options = {"Chụp ảnh", "Chọn từ thư viện"};
        new AlertDialog.Builder(this)
                .setTitle("Thêm ảnh")
                .setItems(options, (d, which) -> {
                    if (which == 0) openCamera();
                    else pickImageLauncher.launch("image/*");
                })
                .show();
    }

    private void openCamera() {
        try {
            File file = File.createTempFile("photo_", ".jpg", getCacheDir());
            cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            takePhotoLauncher.launch(cameraImageUri);
        } catch (Exception e) {
            Toast.makeText(this, "Không mở được camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPhoto(Uri uri) {
        pendingPhotoUri = uri;
        ivPhoto.setVisibility(View.VISIBLE);
        photoPlaceholder.setVisibility(View.GONE);
        Glide.with(this).load(uri).into(ivPhoto);
    }

    // ---------- Lưu ----------
    private void save() {
        String name = etName.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String note = etNote.getText().toString().trim();
        String mapUrl = etMapUrl.getText().toString().trim();

        if (name.isEmpty()) {
            etName.setError(getString(R.string.error_name));
            return;
        }

        int checkedId = chipGroup.getCheckedChipId();
        String category;
        if (checkedId == R.id.chipHotel) category = "khach_san";
        else if (checkedId == R.id.chipCafe) category = "ca_phe";
        else if (checkedId == R.id.chipOther) category = "khac";
        else category = "an_uong";

        Integer rating = (int) ratingBar.getRating();
        if (rating < 1) rating = null;

        String token = new TokenManager(this).getAccessToken();
        if (token == null) {
            Toast.makeText(this, "Phiên đăng nhập đã hết, vui lòng đăng nhập lại", Toast.LENGTH_SHORT).show();
            return;
        }
        String bearer = "Bearer " + token;

        Place place = new Place(
                name,
                address.isEmpty() ? null : address,
                category,
                rating,
                note.isEmpty() ? null : note,
                mapUrl.isEmpty() ? null : mapUrl
        );

        Callback<Place> cb = new Callback<Place>() {
            @Override
            public void onResponse(Call<Place> call, Response<Place> response) {
                if (response.isSuccessful() && response.body() != null) {
                    afterSaved(bearer, response.body());
                } else {
                    showError(response);
                }
            }

            @Override
            public void onFailure(Call<Place> call, Throwable t) {
                Toast.makeText(AddPlaceActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        if (editingPlace != null) {
            ApiClient.getApiService().updatePlace(bearer, editingPlace.getId(), place).enqueue(cb);
        } else {
            ApiClient.getApiService().createPlace(bearer, place).enqueue(cb);
        }
    }

    // Sau khi tạo/sửa xong: nếu có ảnh mới thì upload rồi mới về Main
    private void afterSaved(String bearer, Place saved) {
        if (pendingPhotoUri != null && saved.getId() != null) {
            MultipartBody.Part part = buildImagePart(pendingPhotoUri);
            if (part != null) {
                ApiClient.getApiService().uploadPhoto(bearer, saved.getId(), part).enqueue(new Callback<Place>() {
                    @Override
                    public void onResponse(Call<Place> call, Response<Place> response) {
                        goToMain();
                    }

                    @Override
                    public void onFailure(Call<Place> call, Throwable t) {
                        Toast.makeText(AddPlaceActivity.this, "Lưu ok nhưng upload ảnh lỗi", Toast.LENGTH_SHORT).show();
                        goToMain();
                    }
                });
                return;
            }
        }
        goToMain();
    }

    private void goToMain() {
        Toast.makeText(this, getString(R.string.saved_ok), Toast.LENGTH_SHORT).show();
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    private void showError(Response<Place> response) {
        String message = "Lưu thất bại";
        try {
            if (response.errorBody() != null) {
                ErrorResponse err = new Gson().fromJson(response.errorBody().string(), ErrorResponse.class);
                if (err != null && err.getDetail() != null) message = err.getDetail();
            }
        } catch (Exception e) { /* giữ mặc định */ }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Đọc ảnh từ Uri -> multipart part
    private MultipartBody.Part buildImagePart(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = is.read(chunk)) != -1) buffer.write(chunk, 0, n);
            RequestBody body = RequestBody.create(MediaType.parse("image/*"), buffer.toByteArray());
            return MultipartBody.Part.createFormData("file", "photo.jpg", body);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- Link Maps ----------
    private String extractUrl(String text) {
        Matcher m = Pattern.compile("https?://\\S+").matcher(text);
        if (m.find()) return m.group();
        return null;
    }

    private void resolveMapLink(String url) {
        if (url == null) return;
        url = url.trim();
        if (url.isEmpty()) return;
        if (!MapLinkResolver.isGoogleMapUrl(url)) {
            tilMapUrl.setError("Link Google Maps không hợp lệ");
            return;
        }
        tilMapUrl.setError(null);
        tilMapUrl.setHelperText("Đang lấy thông tin...");
        MapLinkResolver.resolve(url, new MapLinkResolver.ResolveCallback() {
            @Override
            public void onResolved(PlaceInfo info) {
                tilMapUrl.setHelperText(getString(R.string.helper_map_url));
                if (info.getName() != null) etName.setText(info.getName());
                if (info.getAddress() != null) etAddress.setText(info.getAddress());
            }

            @Override
            public void onError(String message) {
                tilMapUrl.setHelperText(getString(R.string.helper_map_url));
                Toast.makeText(AddPlaceActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
