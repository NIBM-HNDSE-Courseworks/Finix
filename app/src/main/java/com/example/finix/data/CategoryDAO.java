package com.example.finix.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CategoryDAO {

    @Insert
    void insert(Category category);

    @Update
    void update(Category category);
    @Delete
    void delete(Category category);

    @Query("SELECT * FROM categories ORDER BY name ASC")
    List<Category> getAllCategories();

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    Category getCategoryByName(String name);

    @Query("SELECT * FROM categories WHERE name LIKE :query ORDER BY name ASC")
    List<Category> searchCategories(String query);
}