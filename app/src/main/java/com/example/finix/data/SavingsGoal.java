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
    private int categoryId; // Added: Links the goal to a category (e.g., 'Vacation Fund')

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "description")
    private String description; // Added: A short description for the goal

    @ColumnInfo(name = "target_amount")
    private double targetAmount;

    @ColumnInfo(name = "target_date")
    private long targetDate;

    // Note: 'current_saved_amount' has been removed.
    // The current saved amount should be calculated dynamically by summing
    // transactions that are specifically linked to this goal's Category.

    // Constructor
    public SavingsGoal(int categoryId, String name, String description, double targetAmount, long targetDate) {
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
    }

    // --- Getters and Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getTargetAmount() { return targetAmount; }
    public void setTargetAmount(double targetAmount) { this.targetAmount = targetAmount; }

    public long getTargetDate() { return targetDate; }
    public void setTargetDate(long targetDate) { this.targetDate = targetDate; }
}