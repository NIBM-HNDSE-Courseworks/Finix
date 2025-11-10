    package com.example.finix.data;

    import androidx.room.Dao;
    import androidx.room.Insert;
    import androidx.room.Delete;
    import androidx.room.OnConflictStrategy;
    import androidx.room.Query;
    import androidx.room.Update;

    import java.util.List;

    @Dao
    public interface BudgetDAO {

        @Insert
        long insert(Budget budget);

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

        // 🆕 FIX: Method required by FinixRepository for synchronization
        @Query("SELECT * FROM budgets WHERE local_id = :localId")
        Budget getBudgetById(int localId);









        // 1. Retrieve All data (for backup)
        @Query("SELECT * FROM budgets")
        List<Budget> getAllBudgetsForBackup();

        // 2. Delete All data (for restore prep)
        @Query("DELETE FROM budgets")
        void deleteAll();

        // 3. Insert All data (for restore)
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        void insertAll(List<Budget> budgets);
    }
