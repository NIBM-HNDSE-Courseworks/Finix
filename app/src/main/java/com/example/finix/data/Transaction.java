package com.example.finix.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

@Entity(tableName = "transactions",
        foreignKeys = @ForeignKey(entity = Category.class,
                parentColumns = "local_id", // Link to Category's local_id
                childColumns = "category_id",
                onDelete = ForeignKey.RESTRICT),
        indices = {@Index(value = {"category_id"})})
public class Transaction {

    // --- Local unique ID for Room (auto-generated) ---
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_id")
    @SerializedName("local_id")
    private int localId;

    // --- Server ID, 0 until synced ---
    @ColumnInfo(name = "id", defaultValue = "0")
    private int id;

    @ColumnInfo(name = "amount")
    private double amount;

    @ColumnInfo(name = "type")
    private String type;

    @ColumnInfo(name = "category_id")
    private int categoryId; // FK to Category.local_id

    @ColumnInfo(name = "date_time")
    private long dateTime;

    @ColumnInfo(name = "description")
    private String description;

    // ✅ Public no-arg constructor for Room
    public Transaction() {}

    // --- Constructor for new Transaction (localId auto-generated) ---
    @Ignore
    public Transaction(double amount, String type, int categoryId, long dateTime, String description) {
        this.amount = amount;
        this.type = type;
        this.categoryId = categoryId;
        this.dateTime = dateTime;
        this.description = description;
        this.id = 0; // Server ID initially 0
    }

    // --- Constructor for mapping existing / server data ---
    @Ignore
    public Transaction(int localId, int id, double amount, String type, int categoryId, long dateTime, String description) {
        this.localId = localId;
        this.id = id;
        this.amount = amount;
        this.type = type;
        this.categoryId = categoryId;
        this.dateTime = dateTime;
        this.description = description;
    }

    // --- Getters & Setters ---
    public int getLocalId() { return localId; }
    public void setLocalId(int localId) { this.localId = localId; }

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
