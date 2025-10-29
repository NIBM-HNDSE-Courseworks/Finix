package com.example.finix.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface SavingsGoalDao {

    @Insert
    void insert(SavingsGoal goal);

    @Delete
    void delete(SavingsGoal goal);

    @Query("SELECT * FROM savings_goal ORDER BY target_date ASC")
    List<SavingsGoal> getAllGoals();

    @Query("SELECT * FROM savings_goal WHERE goal_name = :name ORDER BY target_date ASC")
    List<SavingsGoal> getGoalsByName(String name);
}
