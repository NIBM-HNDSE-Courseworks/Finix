package com.example.finix.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SavingsGoalDAO {

    @Insert
    long insert(SavingsGoal goal);

    @Update
    void update(SavingsGoal goal);

    @Delete
    void delete(SavingsGoal goal);

    @Query("SELECT * FROM savings_goals ORDER BY target_date ASC")
    LiveData<List<SavingsGoal>> getAllGoalsLive();

    @Query("SELECT * FROM savings_goals ORDER BY target_date ASC")
    List<SavingsGoal> getAllGoals();

    @Query("SELECT * FROM savings_goals WHERE local_id = :localId LIMIT 1")
    SavingsGoal getSavingsGoalById(int localId);











    // 1. Retrieve All data (for backup)
    @Query("SELECT * FROM savings_goals")
    List<SavingsGoal> getAllGoalsForBackup();

    // 2. Delete All data (for restore prep)
    @Query("DELETE FROM savings_goals")
    void deleteAll();

    // 3. Insert All data (for restore)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<SavingsGoal> savingsGoals);

}