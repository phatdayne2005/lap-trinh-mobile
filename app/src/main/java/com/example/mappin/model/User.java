package com.example.mappin.model;

import com.google.gson.annotations.SerializedName;

public class User {
    private String email;
    @SerializedName("full_name")
    private String fullName;
    @SerializedName("created_at")
    private String createdAt;

    public User(String email, String fullName, String createdAt) {
        this.email = email;
        this.fullName = fullName;
        this.createdAt = createdAt;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
