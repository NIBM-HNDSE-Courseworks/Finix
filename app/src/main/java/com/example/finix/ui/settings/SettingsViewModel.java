package com.example.finix.ui.settings;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.finix.data.FinixRepository;
import com.example.finix.data.SynchronizationLog; // NEW IMPORT
import java.util.List; // NEW IMPORT

/**
 * ViewModel responsible for SettingsFragment business logic,
 * including managing the synchronization process and accessing logs.
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

    /**
     * NEW: Retrieves all synchronization logs.
     * NOTE: This should ideally be an asynchronous operation (e.g., using LiveData or Coroutines in a production app).
     * For this example, we call the synchronous repository method.
     */
    public List<SynchronizationLog> getAllSyncLogs() {
        return repository.getAllSynchronizationLogs();
    }
}