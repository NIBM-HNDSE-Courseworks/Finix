package com.example.finix.ui.savings;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.finix.data.FinixDatabase;
import com.example.finix.data.SavingsGoal;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SavingsGoalsViewModel extends AndroidViewModel {

    private final FinixDatabase db;
    private final LiveData<List<SavingsGoal>> goals;
    private final ExecutorService executorService;

    public SavingsGoalsViewModel(@NonNull Application application) {
        super(application);
        db = FinixDatabase.getDatabase(application);
        goals = db.savingsGoalDao().getAllGoalsLive();

        // Use Java Executor instead of Kotlin coroutines
        executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<SavingsGoal>> getAllGoals() {
        return goals;
    }

    public void insert(SavingsGoal goal) {
        executorService.execute(() -> db.savingsGoalDao().insert(goal));
    }

    public void delete(SavingsGoal goal) {
        executorService.execute(() -> db.savingsGoalDao().delete(goal));
    }
}
