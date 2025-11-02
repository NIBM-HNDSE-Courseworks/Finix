package com.example.finix.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;
import androidx.room.Update; // Needed for the update method

import java.util.List;

@Dao
public interface CategoryDAO {

    // Version 1 method: Insert and return the row ID
    @Insert
    long insert(Category category);

    // Version 2 method: Update an existing category
    @Update
    void update(Category category);

    // Present in both versions
    @Delete
    void delete(Category category);

    // Present in both versions
    @Query("SELECT * FROM categories ORDER BY name ASC")
    List<Category> getAllCategories();

    // Version 1 method: Get category by ID
    @Query("SELECT * FROM categories WHERE id = :categoryId")
    Category getCategoryById(int categoryId);

    // Version 2 method: Get category by Name
    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    Category getCategoryByName(String name);

    // Version 2 method: Search categories by name
    @Query("SELECT * FROM categories WHERE name LIKE :query ORDER BY name ASC")
    List<Category> searchCategories(String query);
}