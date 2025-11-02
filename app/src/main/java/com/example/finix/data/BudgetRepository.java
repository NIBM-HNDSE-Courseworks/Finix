package com.example.finix.data;

import android.app.Application;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BudgetRepository {

    private final BudgetDAO budgetDao;
    private final ExecutorService executorService;

    public BudgetRepository(Application application) {
        FinixDatabase db = FinixDatabase.getDatabase(application);
        budgetDao = db.budgetDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    // Insert a budget
    public void insert(Budget budget) {
        executorService.execute(() -> budgetDao.insert(budget));
    }

    // Delete a budget
    public void delete(Budget budget) {
        executorService.execute(() -> budgetDao.delete(budget));
    }

    // Get all budgets (Room runs queries off the main thread)
    public List<Budget> getAllBudgets() {
        return budgetDao.getAllBudgets();
    }

}
