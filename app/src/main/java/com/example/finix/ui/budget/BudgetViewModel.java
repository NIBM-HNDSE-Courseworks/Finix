package com.example.finix.ui.budget;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.example.finix.data.Budget;
import com.example.finix.data.BudgetRepository;
import com.example.finix.data.Category;
import com.example.finix.data.FinixDatabase;
import com.example.finix.data.SynchronizationLog;

import java.util.List;

public class BudgetViewModel extends AndroidViewModel {

    private final BudgetRepository repository;

    public BudgetViewModel(@NonNull Application application) {
        super(application);
        repository = new BudgetRepository(application);
    }

    public void insert(Budget budget) {
        repository.insert(budget);
    }

    public void delete(Budget budget) {
        repository.delete(budget);
    }

    public List<Budget> getAllBudgets() {
        return repository.getAllBudgets();
    }

    public void update(Budget budget) {
        new Thread(() -> FinixDatabase.getDatabase(getApplication()).budgetDao().update(budget)).start();
    }

    public void addCategoryWithSync(String name) {
        if (name == null || name.trim().isEmpty()) return;

        new Thread(() -> {
            try {
                FinixDatabase db = FinixDatabase.getDatabase(getApplication());
                Category category = new Category(name.trim());
                long localId = db.categoryDao().insert(category);

                // Add sync log entry
                SynchronizationLog log = new SynchronizationLog(
                        "categories",
                        (int) localId,
                        System.currentTimeMillis(),
                        "PENDING"
                );
                db.synchronizationLogDao().insert(log);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


}
