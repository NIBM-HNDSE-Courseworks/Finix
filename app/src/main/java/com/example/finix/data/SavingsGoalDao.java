package com.example.finix.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SavingsGoalDao {

    @Insert
    void insert(SavingsGoal goal);

    @Update
    void update(SavingsGoal goal);

    @Delete
    void delete(SavingsGoal goal);

    @Query("SELECT * FROM savings_goals ORDER BY target_date ASC")
    LiveData<List<SavingsGoal>> getAllGoalsLive();

    @Query("SELECT * FROM savings_goals ORDER BY target_date ASC")
    List<SavingsGoal> getAllGoals();
}