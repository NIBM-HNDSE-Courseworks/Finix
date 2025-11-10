package com.example.finix.ui.settings;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.finix.data.FinixRepository;
import com.example.finix.data.SynchronizationLog;
import java.util.List;

/**
 * ViewModel responsible for SettingsFragment business logic,
 * including managing the synchronization process and accessing logs,
 * and coordinating backup and restore operations.
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


    public void startSync() {
        repository.synchronizeAllData();
    }

    /**
     * NEW: Initiates the creation of a local backup file at the specified folder URI.
     * @param folderUriString The URI of the folder where the backup should be saved.
     * @param fileName The desired name for the backup file.
     */
    public boolean createBackup(@NonNull String folderUriString, @NonNull String fileName) {
        // Delegate the actual file and database operation to the repository
        return repository.exportDataToBackup(folderUriString, fileName);
    }

    /**
     * MODIFIED: Initiates the restoration of data from a local backup file specified by URI.
     * @param fileUriString The URI of the backup file to restore from.
     */
    public boolean restoreBackup(@NonNull String fileUriString) {
        // Delegate the actual file and database operation to the repository
        return repository.importDataFromBackup(fileUriString);
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