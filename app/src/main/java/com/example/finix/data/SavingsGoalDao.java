package com.example.finix.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Delete;
import androidx.room.Query;
import java.util.List;

@Dao
public interface SavingsGoalDao {

    @Insert
    void insert(SavingsGoal goal);

    @Delete
    void delete(SavingsGoal goal);

    @Query("SELECT * FROM savings_goals ORDER BY target_date ASC")
    List<SavingsGoal> getAllGoals();

    // Optional: get goals by name
    @Query("SELECT * FROM savings_goals WHERE goal_name = :name ORDER BY target_date ASC")
    List<SavingsGoal> getGoalsByName(String name);
}
