package com.example.mappin.network;

import com.example.mappin.model.AuthResponse;
import com.example.mappin.model.LoginRequest;
import com.example.mappin.model.Place;
import com.example.mappin.model.RefreshRequest;
import com.example.mappin.model.RegisterRequest;

import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;

public interface ApiService {
    @POST("auth/register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    @POST("auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @POST("auth/refresh")
    Call<AuthResponse> refreshToken(@Body RefreshRequest body);

    @POST("places")
    Call<Place> createPlace(@Header("Authorization") String bearer, @Body Place place);

    @GET("places")
    Call<List<Place>> getPlaces(@Header("Authorization") String bearer);

    @PUT("places/{id}")
    Call<Place> updatePlace(@Header("Authorization") String bearer, @Path("id") int id, @Body Place place);

    @DELETE("places/{id}")
    Call<Void> deletePlace(@Header("Authorization") String bearer, @Path("id") int id);

    @Multipart
    @POST("places/{id}/photo")
    Call<Place> uploadPhoto(@Header("Authorization") String bearer, @Path("id") int id, @Part MultipartBody.Part file);
}
