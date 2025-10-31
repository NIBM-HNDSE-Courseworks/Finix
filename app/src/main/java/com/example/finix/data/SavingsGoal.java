package com.example.finix.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "savings_goals",
        foreignKeys = @ForeignKey(
                entity = Category.class,
                parentColumns = "id",
                childColumns = "category_id",
                onDelete = ForeignKey.RESTRICT
        )
)
public class SavingsGoal {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "goal_id")
    private int id;

    @ColumnInfo(name = "category_id")
    private int categoryId;

    @ColumnInfo(name = "goal_name")
    private String goalName;

    @ColumnInfo(name = "goal_description")
    private String goalDescription;

    @ColumnInfo(name = "target_amount")
    private double targetAmount;

    @ColumnInfo(name = "target_date")
    private long targetDate;

    // ✅ Room requires a no-arg constructor
    public SavingsGoal() {}

    public SavingsGoal(int categoryId, String goalName, String goalDescription, double targetAmount, long targetDate) {
        this.categoryId = categoryId;
        this.goalName = goalName;
        this.goalDescription = goalDescription;
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
    }

    // --- Getters & Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getGoalName() { return goalName; }
    public void setGoalName(String goalName) { this.goalName = goalName; }

    public String getGoalDescription() { return goalDescription; }
    public void setGoalDescription(String goalDescription) { this.goalDescription = goalDescription; }

    public double getTargetAmount() { return targetAmount; }
    public void setTargetAmount(double targetAmount) { this.targetAmount = targetAmount; }

    public long getTargetDate() { return targetDate; }
    public void setTargetDate(long targetDate) { this.targetDate = targetDate; }
}