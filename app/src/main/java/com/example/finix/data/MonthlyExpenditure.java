package com.example.finix.data;

import com.google.gson.annotations.SerializedName;

public class MonthlyExpenditure {
    @SerializedName("category_id")
    private int category_id;
    private double total_spent;

    private String month_year;

    // getters and setters
    public int getCategory_id() { return category_id; }
    public void setCategory_id(int category_id) { this.category_id = category_id; }

    public double getTotal_spent() { return total_spent; }
    public void setTotal_spent(double total_spent) { this.total_spent = total_spent; }

    public String getMonth_year() { return month_year; }
    public void setMonth_year(String month_year) { this.month_year = month_year; }
}
