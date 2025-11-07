package com.example.finix.ui.savings;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.finix.data.Category;
import com.example.finix.data.FinixDatabase;
import com.example.finix.data.SavingsGoal;

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

    public LiveData<List<SavingsGoal>> getAllGoals() {
        return goals;
    }

    public LiveData<Map<Integer, String>> getCategoryMapLive() {
        return categoryMapLive;
    }

    // --- Categories ---

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
            loadCategories();
        });
    }

    // --- SavingsGoals CRUD ---

    public void insert(SavingsGoal goal) {
        executor.execute(() -> db.savingsGoalDao().insert(goal));
    }

    public void update(SavingsGoal goal) {
        executor.execute(() -> db.savingsGoalDao().update(goal));
    }

    public void delete(SavingsGoal goal) {
        executor.execute(() -> db.savingsGoalDao().delete(goal));
    }

    public void addCategoryWithSync(String name) {
        executor.execute(() -> {
            // 1. Insert category
            long newId = db.categoryDao().insert(new Category(name));

            // 2. Add to sync log
            db.synchronizationLogDao().insert(
                    new com.example.finix.data.SynchronizationLog(
                            "categories",
                            (int)newId,
                            System.currentTimeMillis(),
                            "PENDING"
                    )
            );

            // 3. Reload categories for LiveData
            loadCategories();
        });
    }

}