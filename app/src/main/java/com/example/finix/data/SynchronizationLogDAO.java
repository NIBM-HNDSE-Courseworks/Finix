package com.example.finix.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Delete;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update; // <-- NEW IMPORT
import java.util.List;

@Dao
public interface SynchronizationLogDAO {

    @Insert
    void insert(SynchronizationLog log);

    @Update // <-- NEW METHOD
    void update(SynchronizationLog log);

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

    // 🆕 NEW: Get synchronization log by its primary key (log_id)
    @Query("SELECT * FROM sync_log WHERE log_id = :logId LIMIT 1")
    SynchronizationLog getLogById(int logId);

    @Query("UPDATE sync_log SET status = 'SYNCED', message = 'Successfully synced to server (Server ID: ' || :serverId || ')', last_synced_timestamp = :currentTime WHERE log_id = :localLogId")
    void updateLogStatusToSynced(int localLogId, int serverId, long currentTime);










    // 1. Retrieve All data (for backup)
    // NOTE: If you already have 'getAllLogs()' and it returns a List<SynchronizationLog>, you can skip this one.
    @Query("SELECT * FROM sync_log")
    List<SynchronizationLog> getAllLogsForBackup();

    // 2. Delete All data (for restore prep)
    @Query("DELETE FROM sync_log")
    void deleteAll();

    // 3. Insert All data (for restore)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<SynchronizationLog> logs);
    
    
}