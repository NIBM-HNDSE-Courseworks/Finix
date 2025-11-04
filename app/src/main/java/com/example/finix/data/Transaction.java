package com.example.finix.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;
import androidx.room.Index;

// Defines the table name and now includes an INDEX on category_id
@Entity(tableName = "transactions",
        foreignKeys = @ForeignKey(entity = Category.class,
                // UPDATED: Changed parentColumns from "id" to "local_id"
                parentColumns = "local_id",
                childColumns = "category_id",
                onDelete = ForeignKey.RESTRICT),
        // Index on category_id is correct for the foreign key
        indices = {@Index(value = {"category_id"})})
public class Transaction {

    // Primary Key, auto-generates the ID
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "transaction_id")
    private int id;

    @ColumnInfo(name = "amount")
    private double amount;

    @ColumnInfo(name = "type")
    private String type;

    // Foreign key reference to Category table
    @ColumnInfo(name = "category_id")
    private int categoryId;

    @ColumnInfo(name = "date_time")
    private long dateTime;

    @ColumnInfo(name = "description")
    private String description;

    // Constructor
    public Transaction(double amount, String type, int categoryId, long dateTime, String description) {
        this.amount = amount;
        this.type = type;
        this.categoryId = categoryId;
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

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public long getDateTime() { return dateTime; }
    public void setDateTime(long dateTime) { this.dateTime = dateTime; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}