    package com.example.finix.data;

    import androidx.room.Dao;
    import androidx.room.Insert;
    import androidx.room.Delete;
    import androidx.room.Query;
    import androidx.room.Update;

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
        @Query("SELECT * FROM budgets WHERE category_id = :category ORDER BY start_date DESC")
        List<Budget> getBudgetsByCategory(String category);

        // -- New: budgets within a date range (inclusive)
        @Query("SELECT * FROM budgets WHERE NOT (end_date < :rangeStart OR start_date > :rangeEnd) ORDER BY start_date DESC")
        List<Budget> getBudgetsBetween(long rangeStart, long rangeEnd);

        @Update
        void update(Budget budget);

    }
