package com.example.finix.ui.savings;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.finix.data.Category;
import com.example.finix.data.FinixDatabase;
import com.example.finix.data.SavingsGoal;
import com.example.finix.data.SynchronizationLog; // ✅ Ensure this is imported

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
                map.put(c.getLocalId(), c.getName());
            }
            categoryMapLive.postValue(map);
        });
    }

    public void fetchLatestCategoryMap() {
        loadCategories();
    }


    // --- SavingsGoals CRUD with Sync Logs ---

    public void insert(SavingsGoal goal, Runnable onComplete) {
        executor.execute(() -> {
            long localId = db.savingsGoalDao().insert(goal);
            logGoalSave((int) localId);
            if (onComplete != null) onComplete.run();
        });
    }

    public void update(SavingsGoal goal, Runnable onComplete) {
        executor.execute(() -> {
            db.savingsGoalDao().update(goal);

            // 🔄 Change: Using LOCAL ID for update, mirroring logTransactionUpdate
            logGoalUpdate(goal.getLocalId());

            if (onComplete != null) onComplete.run();
        });
    }

    // SavingsGoalsViewModel.java (Add this or fix your existing method)
    public void delete(SavingsGoal goal, Runnable onComplete) {
        executor.execute(() -> {
            db.savingsGoalDao().delete(goal);

            // 🛑 Change: Using SERVER ID for delete, mirroring logTransactionDelete
            logGoalDelete(goal.getId());

            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    // --- Synchronization Log Methods for Savings Goals ---
    private void logGoalSave(int goalLocalId) {
        SynchronizationLog log = new SynchronizationLog(
                "savings_goals",
                goalLocalId,
                System.currentTimeMillis(),
                "PENDING"
        );
        db.synchronizationLogDao().insert(log);
    }

    private void logGoalUpdate(int goalId) {
        SynchronizationLog log = new SynchronizationLog(
                "savings_goals",
                goalId,
                System.currentTimeMillis(),
                "UPDATED"
        );
        // The insert is called directly on the executor thread of the update() method.
        db.synchronizationLogDao().insert(log);
    }

    private void logGoalDelete(int goalId) {
        SynchronizationLog log = new SynchronizationLog(
                "savings_goals",
                goalId,
                System.currentTimeMillis(),
                "DELETED"
        );
        db.synchronizationLogDao().insert(log);
    }

    public void addCategoryWithSync(String name) {
        executor.execute(() -> {
            // 1️⃣ Insert category into DB
            Category category = new Category(name);
            long newId = db.categoryDao().insert(category);

            // 2️⃣ Create sync log entry (PENDING state)
            db.synchronizationLogDao().insert(
                    new com.example.finix.data.SynchronizationLog(
                            "categories",
                            (int)newId,
                            System.currentTimeMillis(),
                            "PENDING"
                    )
            );

            // 3️⃣ Reload categories for LiveData
            loadCategories();
        });
    }

}