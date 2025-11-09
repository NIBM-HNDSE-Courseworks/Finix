package com.example.finix.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(
        tableName = "savings_goals",
        foreignKeys = @ForeignKey(
                entity = Category.class,
                parentColumns = "local_id",
                childColumns = "category_id",
                onDelete = ForeignKey.RESTRICT
        ),
        indices = {@Index(value = {"category_id"})}
)
public class SavingsGoal {

    // --- Local unique ID for Room (auto-generated) ---
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_id")
    @SerializedName("local_id")
    private int localId;

    // --- Server ID, 0 until synced ---
    @ColumnInfo(name = "id", defaultValue = "0")
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

    // ✅ Public no-arg constructor for Room
    public SavingsGoal() {}

    // --- Constructor for new Goal (localId auto-generated) ---
    @Ignore
    public SavingsGoal(int categoryId, String goalName, String goalDescription, double targetAmount, long targetDate) {
        this.categoryId = categoryId;
        this.goalName = goalName;
        this.goalDescription = goalDescription;
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
        this.id = 0; // Server ID initially 0
    }

    // --- Constructor for mapping existing / server data ---
    @Ignore
    public SavingsGoal(int localId, int id, int categoryId, String goalName, String goalDescription, double targetAmount, long targetDate) {
        this.localId = localId;
        this.id = id;
        this.categoryId = categoryId;
        this.goalName = goalName;
        this.goalDescription = goalDescription;
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
    }

    // --- Getters & Setters ---
    public int getLocalId() { return localId; }
    public void setLocalId(int localId) { this.localId = localId; }

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
