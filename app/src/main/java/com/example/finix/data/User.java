package com.example.finix.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "users",
        indices = {@Index(value = {"email"}, unique = true)}   // <-- UNIQUE EMAIL
)
public class User {

    @PrimaryKey(autoGenerate = true)
    public int id;                     // auto-incremented

    public String username;
    public String email;               // must be @gmail.com
    public String passwordHash;        // BCrypt hash

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }
}