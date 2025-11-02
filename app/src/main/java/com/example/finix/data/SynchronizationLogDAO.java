package com.example.finix.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Delete;
import androidx.room.Query;
import java.util.List;

@Dao
public interface SynchronizationLogDAO {

    @Insert
    void insert(SynchronizationLog log);

    @Delete
    void delete(SynchronizationLog log);

    @Query("SELECT * FROM sync_log ORDER BY last_synced_timestamp DESC")
    List<SynchronizationLog> getAllLogs();

    // Optional: get logs by status
    @Query("SELECT * FROM sync_log WHERE status = :status ORDER BY last_synced_timestamp DESC")
    List<SynchronizationLog> getLogsByStatus(String status);

    // Optional: get logs for a specific table
    @Query("SELECT * FROM sync_log WHERE table_name = :tableName ORDER BY last_synced_timestamp DESC")
    List<SynchronizationLog> getLogsByTable(String tableName);
}
