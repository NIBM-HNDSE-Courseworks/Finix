package com.example.finix.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

// Defines the database structure:
// entities: Lists all the table models (Entities) included in this database.
// version: Must be incremented whenever you change the schema (tables/columns).
// exportSchema: Set to false for simple projects.
@Database(entities = {Transaction.class, Budget.class, SavingsGoal.class, SynchronizationLog.class, Category.class},
        version = 4, // ← increment version
        exportSchema = false)
public abstract class FinixDatabase extends RoomDatabase {

    // --- Data Access Objects (DAOs) ---
    // Room generates the implementation for these abstract methods.
    public abstract TransactionDao transactionDao();
    public abstract BudgetDao budgetDao();
    public abstract SavingsGoalDao savingsGoalDao();
    public abstract SynchronizationLogDao synchronizationLogDao();
    public abstract CategoryDAO categoryDao(); // ← Add this


    // --- Singleton Implementation ---
    private static volatile FinixDatabase INSTANCE;
    private static final String DATABASE_NAME = "finix_database";

    /**
     * Gets the singleton instance of the FinixDatabase.
     * Prevents multiple database instances from being opened simultaneously.
     * @param context The application context.
     * @return The single instance of the database.
     */
    public static FinixDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (FinixDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    FinixDatabase.class,
                                    DATABASE_NAME)
                            // 💡 FIX: Added to allow Room to clear the database and recreate tables when a migration is missing.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}