package com.example.finix.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;
import java.util.List;

@Dao
public interface TransactionDao {

    @Insert
    void insert(Transaction transaction);

    @Delete
    void delete(Transaction transaction);

    @Query("SELECT * FROM transactions ORDER BY date_time DESC")
    List<Transaction> getAllTransactions();

    // Optional: filter by type
    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date_time DESC")
    List<Transaction> getTransactionsByType(String type);
}
