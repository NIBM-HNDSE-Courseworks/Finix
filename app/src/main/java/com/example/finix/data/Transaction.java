package com.example.finix.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

// Defines the table name
@Entity(tableName = "transactions")
public class Transaction {

    // Primary Key, auto-generates the ID
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "transaction_id")
    private int id;

    // Not Null constraint is implicitly enforced if you pass a primitive double
    @ColumnInfo(name = "amount")
    private double amount;

    // Not Null constraint for type (Expense/Income)
    @ColumnInfo(name = "type")
    private String type;

    @ColumnInfo(name = "category")
    private String category;

    // Using long for the timestamp/date for better compatibility and sorting
    @ColumnInfo(name = "date_time")
    private long dateTime;

    @ColumnInfo(name = "description")
    private String description;

    // Constructor (Room uses this to create objects)
    public Transaction(double amount, String type, String category, long dateTime, String description) {
        this.amount = amount;
        this.type = type;
        this.category = category;
        this.dateTime = dateTime;
        this.description = description;
    }

    // --- Getters and Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public long getDateTime() { return dateTime; }
    public void setDateTime(long dateTime) { this.dateTime = dateTime; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}