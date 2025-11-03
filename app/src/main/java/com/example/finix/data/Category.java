package com.example.finix.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "categories")
public class Category {

    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "id")
    private int id;

    @ColumnInfo(name = "name")
    private String name;

    // 1. Explicit no-arg constructor for Room
    public Category() {
        // Required empty public constructor
    }

    // 2. Constructor for creating a *new* Category (ID is manually set to a unique negative value)
    @Ignore
    public Category(String name) {
        // --- CRITICAL FIX: Manually assign a unique negative ID ---
        // Using System.currentTimeMillis() * -1 ensures uniqueness and avoids conflict
        // with positive ORDS-assigned IDs (if ORDS IDs are positive).
        this.id = (int) (System.currentTimeMillis() * -1);
        this.name = name;
    }

    // 3. Constructor for existing data or mapping remote data (ID is known)
    public Category(int id, String name) {
        this.id = id;
        this.name = name;
    }

    // --- Getters & Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}