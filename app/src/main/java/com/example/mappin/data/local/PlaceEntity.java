package com.example.mappin.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Bản sao địa điểm lưu dưới máy (Room) để xem offline. Ánh xạ 1-1 với model Place.
 */
@Entity(tableName = "places")
public class PlaceEntity {
    @PrimaryKey
    public int id;
    public String placeName;
    public String address;
    public String category;
    public Integer rating;   // nullable
    public String note;
    public String mapUrl;
    public String imageUrl;
    public String createdAt; // ISO string -> sort chuỗi DESC vẫn đúng thứ tự thời gian

    public PlaceEntity() {}

    @NonNull
    @Override
    public String toString() {
        return "PlaceEntity{" + id + ", " + placeName + "}";
    }
}
