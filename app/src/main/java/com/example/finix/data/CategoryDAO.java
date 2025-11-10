package com.example.finix.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Delete;
import androidx.room.Update; // Needed for the update method

import java.util.List;

@Dao
public interface CategoryDAO {

    // Insert and return the generated localId (long)
    @Insert
    long insert(Category category);

    // Update an existing category
    @Update
    void update(Category category);

    @Delete
    void delete(Category category);

    @Query("SELECT * FROM categories ORDER BY name ASC")
    List<Category> getAllCategories();

    // Change: Add this new LiveData method
    @Query("SELECT * FROM categories ORDER BY name ASC")
    LiveData<List<Category>> getAllCategoriesLive();

    // Query updated to search by local_id, as it is the Room Primary Key and
    // the value stored in sync_log.record_id.
    @Query("SELECT * FROM categories WHERE local_id = :localId")
    Category getCategoryById(int localId);

    // Get category by Name
    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    Category getCategoryByName(String name);

    // Search categories by name
    @Query("SELECT * FROM categories WHERE name LIKE :query ORDER BY name ASC")
    List<Category> searchCategories(String query);











    // 1. Retrieve All data (for backup)
    @Query("SELECT * FROM categories")
    List<Category> getAllCategoriesForBackup();

    // 2. Delete All data (for restore prep)
    @Query("DELETE FROM categories")
    void deleteAll();

    // 3. Insert All data (for restore) - REPLACE handles potential ID conflicts during restore
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Category> categories);
}