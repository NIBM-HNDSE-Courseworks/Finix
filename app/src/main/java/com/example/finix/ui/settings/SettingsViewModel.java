package com.example.finix.ui.settings;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.finix.data.FinixRepository;

/**
 * ViewModel responsible for SettingsFragment business logic,
 * including managing the synchronization process.
 */
public class SettingsViewModel extends AndroidViewModel {

    private final FinixRepository repository;
    private final LiveData<FinixRepository.SynchronizationState> syncStatus;

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        repository = new FinixRepository(application);
        syncStatus = repository.getSyncStatus();
    }

    /**
     * LiveData representing the current synchronization status.
     * The Fragment observes this to update the UI.
     */
    public LiveData<FinixRepository.SynchronizationState> getSyncStatus() {
        return syncStatus;
    }

    /**
     * Initiates the synchronization process for all categories.
     */
    public void startCategorySync() {
        repository.synchronizeCategories();
    }

    // You can add more settings-related logic here (e.g., saving sync frequency preference)
}
