package com.example.finix.ui.budget;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.example.finix.data.Budget;
import com.example.finix.data.BudgetRepository;
import com.example.finix.data.Category; // Keep import for completeness, even if not used
import com.example.finix.data.FinixDatabase;
import com.example.finix.data.SynchronizationLog;

import java.util.List;

public class BudgetViewModel extends AndroidViewModel {

    private final BudgetRepository repository;
    private final FinixDatabase db; // Reference to the database for logging

    public BudgetViewModel(@NonNull Application application) {
        super(application);
        repository = new BudgetRepository(application);
        db = FinixDatabase.getDatabase(application); // Initialize DB reference
    }


    // BudgetViewModel.java

    public void insert(Budget budget, Runnable onComplete) { // 🆕 ADD Runnable
        new Thread(() -> {
            long localId = db.budgetDao().insert(budget);
            logBudgetSave((int) localId);

            if (onComplete != null) { // 🆕 EXECUTE CALLBACK
                onComplete.run();
            }
        }).start();
    }

    public void update(Budget budget, Runnable onComplete) { // 🆕 ADD Runnable
        new Thread(() -> {
            db.budgetDao().update(budget);
            logBudgetUpdate(budget.getLocalId());

            if (onComplete != null) { // 🆕 EXECUTE CALLBACK
                onComplete.run();
            }
        }).start();
    }
    // Delete method should also take a callback for consistency.
    public void delete(Budget budget, Runnable onComplete) {
        new Thread(() -> {
            db.budgetDao().delete(budget);
            logBudgetDelete(budget.getId() == 0 ? budget.getLocalId() : budget.getId());

            if (onComplete != null) {
                onComplete.run();
            }
        }).start();
    }


    public List<Budget> getAllBudgets() {
        return repository.getAllBudgets();
    }





    // --- Synchronization Log Methods for Budget ---

    // 🆕 NEW: Add sync log for a saved budget
    public void logBudgetSave(int budgetLocalId) {
        new Thread(() -> {
            SynchronizationLog log = new SynchronizationLog(
                    "budgets",
                    budgetLocalId,
                    System.currentTimeMillis(),
                    "PENDING"
            );
            db.synchronizationLogDao().insert(log);
        }).start();
    }

    // 🆕 NEW: Add sync log for an updated budget
    public void logBudgetUpdate(int budgetLocalId) {
        new Thread(() -> {
            SynchronizationLog log = new SynchronizationLog(
                    "budgets",
                    budgetLocalId,
                    System.currentTimeMillis(),
                    "UPDATED"
            );
            db.synchronizationLogDao().insert(log);
        }).start();
    }

    // 🆕 NEW: Add sync log for a deleted budget
    public void logBudgetDelete(int budgetId) { // budgetId here should be the server ID if sync log uses server IDs for DELETED
        new Thread(() -> {
            SynchronizationLog log = new SynchronizationLog(
                    "budgets",
                    budgetId,
                    System.currentTimeMillis(),
                    "DELETED"
            );
            db.synchronizationLogDao().insert(log);
        }).start();
    }
}