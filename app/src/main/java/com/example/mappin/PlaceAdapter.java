package com.example.mappin;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mappin.model.Place;

import java.util.ArrayList;
import java.util.List;

public class PlaceAdapter extends RecyclerView.Adapter<PlaceAdapter.PlaceViewHolder> {

    private final List<Place> places = new ArrayList<>();

    // Bấm vào 1 card -> báo ra ngoài (mở màn chi tiết)
    public interface OnPlaceClickListener {
        void onClick(Place place);
    }

    private OnPlaceClickListener listener;

    public void setOnPlaceClickListener(OnPlaceClickListener listener) {
        this.listener = listener;
    }

    // Cập nhật dữ liệu mới rồi vẽ lại
    public void setPlaces(List<Place> newPlaces) {
        places.clear();
        if (newPlaces != null) places.addAll(newPlaces);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_place, parent, false);
        return new PlaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaceViewHolder holder, int position) {
        Place place = places.get(position);
        holder.tvName.setText(place.getPlaceName());
        holder.tvAddress.setText(place.getAddress() != null
                ? com.example.mappin.util.AddressUtils.stripPostalCode(place.getAddress()) : "");
        holder.tvCategory.setText(categoryLabel(place.getCategory()));
        holder.tvRating.setText(place.getRating() != null ? "★ " + place.getRating() : "");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(place);
        });
    }

    @Override
    public int getItemCount() {
        return places.size();
    }

    // Đổi mã loại -> nhãn hiển thị (ngược với lúc lưu)
    private String categoryLabel(String code) {
        if (code == null) return "📍 Khác";
        switch (code) {
            case "an_uong":   return "🍜 Ăn uống";
            case "khach_san": return "🏨 Khách sạn";
            case "ca_phe":    return "☕ Cà phê";
            default:          return "📍 Khác";
        }
    }

    static class PlaceViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvAddress, tvCategory, tvRating;

        PlaceViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvAddress = itemView.findViewById(R.id.tvAddress);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvRating = itemView.findViewById(R.id.tvRating);
        }
    }
}
