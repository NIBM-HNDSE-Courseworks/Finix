package com.example.finix.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Delete;
import androidx.room.Query;
import java.util.List;

@Dao
public interface BudgetDao {

    @Insert
    void insert(Budget budget);

    @Delete
    void delete(Budget budget);

    @Query("SELECT * FROM budgets ORDER BY start_date DESC")
    List<Budget> getAllBudgets();

    // Optional: get budgets by category
    @Query("SELECT * FROM budgets WHERE category = :category ORDER BY start_date DESC")
    List<Budget> getBudgetsByCategory(String category);
}
