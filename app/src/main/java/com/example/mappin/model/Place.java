package com.example.mappin.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class Place implements Serializable {
    private Integer id;
    @SerializedName("place_name")
    private String placeName;
    private String address;
    private String category;
    private Integer rating;
    private String note;
    @SerializedName("map_url")
    private String mapUrl;
    @SerializedName("image_url")
    private String imageUrl;
    @SerializedName("created_at")
    private String createdAt;

    public Place(String placeName, String address, String category, Integer rating, String note, String mapUrl) {
        this.placeName = placeName;
        this.address = address;
        this.category = category;
        this.rating = rating;
        this.note = note;
        this.mapUrl = mapUrl;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPlaceName() {
        return placeName;
    }

    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getMapUrl() {
        return mapUrl;
    }

    public void setMapUrl(String mapUrl) {
        this.mapUrl = mapUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
