package com.example.finix.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {Category.class, SavingsGoal.class, Transaction.class, Budget.class, SynchronizationLog.class},
        version = 2, //
        exportSchema = false
)
public abstract class FinixDatabase extends RoomDatabase {

    public abstract CategoryDAO categoryDao();
    public abstract SavingsGoalDao savingsGoalDao();
    public abstract TransactionDao transactionDao();
    public abstract BudgetDao budgetDao();
    public abstract SynchronizationLogDao synchronizationLogDao();

    private static volatile FinixDatabase INSTANCE;
    private static final String DATABASE_NAME = "finix_database";

    public static FinixDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (FinixDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    FinixDatabase.class,
                                    DATABASE_NAME)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
