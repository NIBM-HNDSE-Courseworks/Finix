package com.example.finix.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

// Setting the entity for the budgets table, now with a Foreign Key to Category
@Entity(tableName = "budgets",
        indices = {@Index(value = {"category_id"})}, // Fix applied here
        foreignKeys = @ForeignKey(entity = Category.class,
                parentColumns = "id",
                childColumns = "category_id",
                onDelete = ForeignKey.RESTRICT))
public class Budget {

    // Primary Key
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "budget_id")
    private int id;

    // Updated to use the Foreign Key category_id
    @ColumnInfo(name = "category_id")
    private int categoryId;

    @ColumnInfo(name = "budgeted_amount")
    private double budgetedAmount;

    @ColumnInfo(name = "start_date")
    private long startDate;

    @ColumnInfo(name = "end_date")
    private long endDate;

    public Budget(){};
    @Ignore
    // Constructor updated
    public Budget(int categoryId, double budgetedAmount, long startDate, long endDate) {
        this.categoryId = categoryId;
        this.budgetedAmount = budgetedAmount;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    // --- Getters and Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCategoryId() { return categoryId; } // Updated getter
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; } // Updated setter

    public double getBudgetedAmount() { return budgetedAmount; }
    public void setBudgetedAmount(double budgetedAmount) { this.budgetedAmount = budgetedAmount; }

    public long getStartDate() { return startDate; }
    public void setStartDate(long startDate) { this.startDate = startDate; }

    public long getEndDate() { return endDate; }
    public void setEndDate(long endDate) { this.endDate = endDate; }
}