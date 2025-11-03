package com.example.finix.ui.savings;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.finix.data.Category;
import com.example.finix.data.FinixDatabase;
import com.example.finix.data.SavingsGoal;
import com.example.finix.data.TransactionDAO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SavingsGoalsViewModel extends AndroidViewModel {

    private final FinixDatabase db;
    private final LiveData<List<SavingsGoal>> goals;
    private final MutableLiveData<Map<Integer, String>> categoryMapLive = new MutableLiveData<>(new HashMap<>());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SavingsGoalsViewModel(@NonNull Application app) {
        super(app);
        db = FinixDatabase.getDatabase(app);
        goals = db.savingsGoalDao().getAllGoalsLive();
        loadCategories();
    }

    // --- GETTERS ---

    public LiveData<List<SavingsGoal>> getAllGoals() {
        return goals;
    }

    public LiveData<Map<Integer, String>> getCategoryMapLive() {
        return categoryMapLive;
    }

    // ------------------------------------------------------------
    // CATEGORY OPERATIONS
    // ------------------------------------------------------------
    private void loadCategories() {
        executor.execute(() -> {
            Map<Integer, String> map = new HashMap<>();
            List<Category> all = db.categoryDao().getAllCategories();
            for (Category c : all) {
                map.put(c.getId(), c.getName());
            }
            categoryMapLive.postValue(map);
        });
    }

    public void fetchLatestCategoryMap() {
        loadCategories();
    }

    public void addCategory(String name) {
        executor.execute(() -> {
            db.categoryDao().insert(new Category(name));
            loadCategories(); // refresh list
        });
    }

    // ------------------------------------------------------------
    // SAVINGS GOAL CRUD
    // ------------------------------------------------------------
    public void insert(SavingsGoal goal) {
        executor.execute(() -> db.savingsGoalDao().insert(goal));
    }

    public void update(SavingsGoal goal) {
        executor.execute(() -> db.savingsGoalDao().update(goal));
    }

    public void delete(SavingsGoal goal) {
        executor.execute(() -> db.savingsGoalDao().delete(goal));
    }

    // ------------------------------------------------------------
    // 💰 TRANSACTION-BASED PROGRESS CALCULATION
    // ------------------------------------------------------------
    public double getCurrentSavings() {
        TransactionDAO tDao = db.transactionDao();


        Double income = tDao.getTotalIncome();
        Double expense = tDao.getTotalExpense();

        if (income == null) income = 0.0;
        if (expense == null) expense = 0.0;

        // returns (total income - total expense)
        return income - expense;
    }

    /**
     * Optionally, if you later want to calculate progress for a specific category:
     */
    public double getSavingsByCategory(int categoryId) {
        TransactionDAO tDao = db.transactionDao();


        Double income = tDao.getTotalIncome();  // you can modify this to filter by category if needed
        Double expense = tDao.getTotalExpense();

        if (income == null) income = 0.0;
        if (expense == null) expense = 0.0;

        return income - expense;
    }
}
