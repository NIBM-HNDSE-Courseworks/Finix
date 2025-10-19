package com.example.finix.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

// Define a ForeignKey to link SavingsGoal to a Category
@Entity(tableName = "savings_goals",
        foreignKeys = @ForeignKey(entity = Category.class,
                parentColumns = "id", // The column in the Category table
                childColumns = "category_id", // The column in this table
                onDelete = ForeignKey.RESTRICT)) // Prevents deleting a category if a goal uses it
public class SavingsGoal {

    // Primary Key
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "goal_id")
    private int id;

    // Foreign Key to the Category table
    @ColumnInfo(name = "category_id")
    private int categoryId; // Links the goal to a category (e.g., 'Vacation Fund')

    // Updated: Changed name to goalName and column name to "goal_name"
    @ColumnInfo(name = "goal_name")
    private String goalName;

    @ColumnInfo(name = "goal_description")
    private String goalDescription; // A short description for the goal

    @ColumnInfo(name = "target_amount")
    private double targetAmount;

    @ColumnInfo(name = "target_date")
    private long targetDate;

    // Note: 'current_saved_amount' has been removed.

    // Constructor
    public SavingsGoal(int categoryId, String goalName, String goalDescription, double targetAmount, long targetDate) {
        this.categoryId = categoryId;
        this.goalName = goalName; // Updated parameter
        this.goalDescription = goalDescription;
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
    }

    // --- Getters and Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    // Updated Getters and Setters
    public String getGoalName() { return goalName; }
    public void setGoalName(String goalName) { this.goalName = goalName; }

    public String getGoalDescription() { return goalDescription; }
    public void setDescription(String description) { this.goalDescription = description; }

    public double getTargetAmount() { return targetAmount; }
    public void setTargetAmount(double targetAmount) { this.targetAmount = targetAmount; }

    public long getTargetDate() { return targetDate; }
    public void setTargetDate(long targetDate) { this.targetDate = targetDate; }
}