package com.example.mappin.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PlaceDao {

    @Query("SELECT * FROM places ORDER BY createdAt DESC")
    List<PlaceEntity> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PlaceEntity> places);

    @Query("DELETE FROM places")
    void clearAll();

    @Query("DELETE FROM places WHERE id = :id")
    void deleteById(int id);
}
