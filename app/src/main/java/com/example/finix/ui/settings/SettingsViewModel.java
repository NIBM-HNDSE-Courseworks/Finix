package com.example.finix.ui.settings;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.annotation.NonNull;
import android.util.Log;

import com.example.finix.data.FinixRepository;

public class SettingsViewModel extends AndroidViewModel {
    private static final String LOG_TAG = "SettingsViewModel"; // ADDED: Log Tag

    private final FinixRepository repository;

    private final MutableLiveData<String> syncStatus = new MutableLiveData<>();
    private final MutableLiveData<Integer> syncProgress = new MutableLiveData<>();
    private boolean isSyncing = false;

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        // Log initialization and repository creation
        Log.d(LOG_TAG, "Initializing SettingsViewModel and FinixRepository.");
        repository = new FinixRepository(application, syncStatus, syncProgress);
    }

    public void syncCategories() {
        // Log the sync attempt and current state
        Log.d(LOG_TAG, "Attempting to start syncCategories. Current isSyncing: " + isSyncing);

        if (isSyncing) {
            Log.w(LOG_TAG, "Sync already in progress. Aborting new sync request.");
            return;
        }

        isSyncing = true;
        // Log the initiation process
        Log.i(LOG_TAG, "Sync started. Setting initial status and progress to 0.");
        syncStatus.setValue("Initializing sync...");
        syncProgress.setValue(0);

        repository.syncCategories();
        Log.d(LOG_TAG, "Called repository.syncCategories(). Data synchronization delegated to Repository.");
    }

    public void setSyncing(boolean syncing) {
        if (this.isSyncing != syncing) {
            // Log when the synchronization status changes
            Log.i(LOG_TAG, "Sync state transition: " + this.isSyncing + " -> " + syncing);
            this.isSyncing = syncing;
        } else {
            Log.v(LOG_TAG, "setSyncing called but state is already " + syncing);
        }
    }

    public LiveData<String> getSyncStatus() {
        return syncStatus;
    }

    public LiveData<Integer> getSyncProgress() {
        return syncProgress;
    }

    public boolean isSyncing() {
        return isSyncing;
    }
}
