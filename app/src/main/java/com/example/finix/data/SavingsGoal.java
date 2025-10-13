package com.example.finix.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "savings_goals")
public class SavingsGoal {

    // Primary Key
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "goal_id")
    private int id;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "target_amount")
    private double targetAmount;

    // Stores the current amount saved towards the goal
    @ColumnInfo(name = "current_saved_amount")
    private double currentSavedAmount;

    @ColumnInfo(name = "target_date")
    private long targetDate;

    // Constructor
    public SavingsGoal(String name, double targetAmount, double currentSavedAmount, long targetDate) {
        this.name = name;
        this.targetAmount = targetAmount;
        this.currentSavedAmount = currentSavedAmount;
        this.targetDate = targetDate;
    }

    // --- Getters and Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getTargetAmount() { return targetAmount; }
    public void setTargetAmount(double targetAmount) { this.targetAmount = targetAmount; }

    public double getCurrentSavedAmount() { return currentSavedAmount; }
    public void setCurrentSavedAmount(double currentSavedAmount) { this.currentSavedAmount = currentSavedAmount; }

    public long getTargetDate() { return targetDate; }
    public void setTargetDate(long targetDate) { this.targetDate = targetDate; }
}