package com.example.finix.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;
import java.util.List;

@Dao
public interface TransactionDao {

    // ➕ Insert a new transaction
    @Insert
    void insert(Transaction transaction);

    // 🔁 Update an existing transaction
    @Update
    void update(Transaction transaction);

    // ❌ Delete a specific transaction
    @Delete
    void delete(Transaction transaction);

    // 📋 Get all transactions (newest first)
    @Query("SELECT * FROM transactions ORDER BY date_time DESC")
    List<Transaction> getAllTransactions();

    // 💰 Get all transactions by type (Income/Expense)
    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date_time DESC")
    List<Transaction> getTransactionsByType(String type);

    // 🔍 Get transactions by category_id and type
    @Query("SELECT * FROM transactions WHERE type = :type AND category_id = :categoryId ORDER BY date_time DESC")
    List<Transaction> getTransactionsByTypeAndCategory(String type, int categoryId);

    // 🆕 NEW: Get all distinct month/year timestamps
    // We get the raw long, then format in the ViewModel
    @Query("SELECT DISTINCT date_time FROM transactions ORDER BY date_time DESC")
    List<Long> getDistinctMonthYear();
}