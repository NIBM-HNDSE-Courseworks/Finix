package com.example.finix.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

// Setting the entity for the budgets table
@Entity(tableName = "budgets")
public class Budget {

    // Primary Key
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "budget_id")
    private int id;

    // Assuming the user sets a budget for a single category per period
    @ColumnInfo(name = "category")
    private String category;

    @ColumnInfo(name = "budgeted_amount")
    private double budgetedAmount;

    @ColumnInfo(name = "start_date")
    private long startDate;

    @ColumnInfo(name = "end_date")
    private long endDate;

    // Constructor
    public Budget(String category, double budgetedAmount, long startDate, long endDate) {
        this.category = category;
        this.budgetedAmount = budgetedAmount;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    // --- Getters and Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getBudgetedAmount() { return budgetedAmount; }
    public void setBudgetedAmount(double budgetedAmount) { this.budgetedAmount = budgetedAmount; }

    public long getStartDate() { return startDate; }
    public void setStartDate(long startDate) { this.startDate = startDate; }

    public long getEndDate() { return endDate; }
    public void setEndDate(long endDate) { this.endDate = endDate; }
}