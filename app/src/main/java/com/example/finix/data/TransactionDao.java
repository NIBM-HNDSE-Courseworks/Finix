package com.example.finix.data;

import androidx.lifecycle.LiveData;
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

    // 💰 Get all transactions by type (Income/Expense) — now case-insensitive
    @Query("SELECT * FROM transactions WHERE LOWER(type) = LOWER(:type) ORDER BY date_time DESC")
    List<Transaction> getTransactionsByType(String type);

    // 🔍 Get transactions by category_id and type — now case-insensitive
    @Query("SELECT * FROM transactions WHERE LOWER(type) = LOWER(:type) AND category_id = :categoryId ORDER BY date_time DESC")
    List<Transaction> getTransactionsByTypeAndCategory(String type, int categoryId);

    // 🆕 NEW: Get all distinct month/year timestamps
    @Query("SELECT DISTINCT date_time FROM transactions ORDER BY date_time DESC")
    List<Long> getDistinctMonthYear();

    // 💰 FIX: Synchronous query for previous month total (case-insensitive)
    @Query("SELECT SUM(amount) FROM transactions WHERE LOWER(type) = LOWER(:type) AND date_time BETWEEN :startTime AND :endTime")
    Double getPreviousMonthTotalSync(String type, long startTime, long endTime);

    // 📈 NEW: Get all transactions of a type within a date range (case-insensitive)
    @Query("SELECT * FROM transactions WHERE LOWER(type) = LOWER(:type) AND date_time BETWEEN :startTime AND :endTime ORDER BY date_time DESC")
    LiveData<List<Transaction>> getTransactionsByTypeAndDateRange(String type, long startTime, long endTime);

    // 💵 Get total income (case-insensitive)
    @Query("SELECT SUM(amount) FROM transactions WHERE LOWER(type) = 'income'")
    Double getTotalIncome();

    // 💸 Get total expense (case-insensitive)
    @Query("SELECT SUM(amount) FROM transactions WHERE LOWER(type) = 'expense'")
    Double getTotalExpense();
}
