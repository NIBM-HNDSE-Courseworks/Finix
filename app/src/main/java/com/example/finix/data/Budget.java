package com.example.finix.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

// Setting the entity for the budgets table, now with a Foreign Key to Category
@Entity(tableName = "budgets",
        indices = {@Index(value = {"category_id"})},
        foreignKeys = @ForeignKey(entity = Category.class,
                // Reference the 'local_id' column in the Category entity
                parentColumns = "local_id",
                childColumns = "category_id",
                onDelete = ForeignKey.RESTRICT))
public class Budget {

    // --- Local unique ID for Room (auto-generated) ---
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_id")
    @SerializedName("local_id")
    private int localId;

    // --- Server ID, 0 until synced ---
    @ColumnInfo(name = "id", defaultValue = "0")
    private int id;

    // Foreign Key to Category.local_id
    @ColumnInfo(name = "category_id")
    private int categoryId;

    @ColumnInfo(name = "budgeted_amount")
    private double budgetedAmount;

    @ColumnInfo(name = "start_date")
    private long startDate;

    @ColumnInfo(name = "end_date")
    private long endDate;

    // ✅ Public no-arg constructor for Room
    public Budget(){};

    // --- Constructor for new Budget (localId auto-generated) ---
    @Ignore
    public Budget(int categoryId, double budgetedAmount, long startDate, long endDate) {
        this.categoryId = categoryId;
        this.budgetedAmount = budgetedAmount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.id = 0; // Server ID initially 0
    }

    // --- Constructor for mapping existing / server data ---
    @Ignore
    public Budget(int localId, int id, int categoryId, double budgetedAmount, long startDate, long endDate) {
        this.localId = localId;
        this.id = id;
        this.categoryId = categoryId;
        this.budgetedAmount = budgetedAmount;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    // --- Getters and Setters ---
    public int getLocalId() { return localId; }
    public void setLocalId(int localId) { this.localId = localId; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public double getBudgetedAmount() { return budgetedAmount; }
    public void setBudgetedAmount(double budgetedAmount) { this.budgetedAmount = budgetedAmount; }

    public long getStartDate() { return startDate; }
    public void setStartDate(long startDate) { this.startDate = startDate; }

    public long getEndDate() { return endDate; }
    public void setEndDate(long endDate) { this.endDate = endDate; }
}