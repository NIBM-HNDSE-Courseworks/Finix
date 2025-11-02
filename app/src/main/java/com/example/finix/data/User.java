package com.example.finix.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index; // 💡 Import the Index class

// Defines the table name and includes a UNIQUE index on the email column
@Entity(tableName = "users",
        // 🚨 ADDED: Unique index on 'email' to enforce that each user must have a unique email
        indices = {@Index(value = {"email"}, unique = true)})
public class User {

    // Primary Key, auto-generates the ID
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "user_id")
    private int id;

    @ColumnInfo(name = "username")
    private String username;

    // This column must have a unique value across all rows, enforced by the index above
    @ColumnInfo(name = "email")
    private String email;

    @ColumnInfo(name = "password_hash")
    private String passwordHash; // Storing a hash, not the plaintext password

    // Constructor
    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    // --- Getters and Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}