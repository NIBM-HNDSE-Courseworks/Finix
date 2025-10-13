package com.example.finix.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sync_log")
public class SynchronizationLog {

    // Primary Key
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "log_id")
    private int id;

    // Name of the table the record belongs to (e.g., "transactions", "budgets")
    @ColumnInfo(name = "table_name")
    private String tableName;

    // The primary key of the record in its respective table (e.g., transaction_id)
    @ColumnInfo(name = "record_id")
    private int recordId;

    // Timestamp of the last successful sync attempt
    @ColumnInfo(name = "last_synced_timestamp")
    private long lastSyncedTimestamp;

    // Status: 'PENDING', 'SYNCED', 'CONFLICT', 'DELETED_LOCAL'
    @ColumnInfo(name = "status")
    private String status;

    // Constructor
    public SynchronizationLog(String tableName, int recordId, long lastSyncedTimestamp, String status) {
        this.tableName = tableName;
        this.recordId = recordId;
        this.lastSyncedTimestamp = lastSyncedTimestamp;
        this.status = status;
    }

    // --- Getters and Setters ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public int getRecordId() { return recordId; }
    public void setRecordId(int recordId) { this.recordId = recordId; }

    public long getLastSyncedTimestamp() { return lastSyncedTimestamp; }
    public void setLastSyncedTimestamp(long lastSyncedTimestamp) { this.lastSyncedTimestamp = lastSyncedTimestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}