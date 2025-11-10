package com.example.finix.data;

import com.google.gson.annotations.SerializedName;

public class BudgetAdherence {

    @SerializedName("budget_id")
    private int budgetId;

    @SerializedName("category")
    private String category;

    @SerializedName("budgeted_amount")
    private double budgetedAmount;

    @SerializedName("actual_spent")
    private double actualSpent;

    @SerializedName("pct_of_budget")
    private double pctOfBudget;

    @SerializedName("adherence_status")
    private String adherenceStatus;

    // Getters and Setters
    public int getBudgetId() { return budgetId; }
    public void setBudgetId(int budgetId) { this.budgetId = budgetId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getBudgetedAmount() { return budgetedAmount; }
    public void setBudgetedAmount(double budgetedAmount) { this.budgetedAmount = budgetedAmount; }

    public double getActualSpent() { return actualSpent; }
    public void setActualSpent(double actualSpent) { this.actualSpent = actualSpent; }

    public double getPctOfBudget() { return pctOfBudget; }
    public void setPctOfBudget(double pctOfBudget) { this.pctOfBudget = pctOfBudget; }

    public String getAdherenceStatus() { return adherenceStatus; }
    public void setAdherenceStatus(String adherenceStatus) { this.adherenceStatus = adherenceStatus; }
}
