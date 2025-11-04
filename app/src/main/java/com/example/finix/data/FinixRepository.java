package com.example.finix.data;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class FinixRepository {

    private static final String TAG = "FinixRepository";
    private static final String BASE_URL = "http://192.168.8.182:8080/ords/"; // Use 10.0.2.2 for Android Emulator host loopback

    private final CategoryDAO categoryDAO;
    private final SynchronizationLogDAO syncLogDAO;
    private final CategoryService categoryService;
    private final ExecutorService executorService;

    // LiveData to expose synchronization status updates
    private final MutableLiveData<SynchronizationState> syncStatusLive = new MutableLiveData<>();

    // Enum to represent the state of synchronization
    public enum SynchronizationState {
        IDLE, CHECKING, PROCESSING, SUCCESS, ERROR, NO_CHANGES
    }

    public FinixRepository(Context context) {
        // Initialize DAOs
        FinixDatabase db = FinixDatabase.getDatabase(context);
        categoryDAO = db.categoryDao();
        syncLogDAO = db.synchronizationLogDao();

        // Initialize Executor for background database operations
        executorService = Executors.newFixedThreadPool(4);

        // Initialize Retrofit and API Service
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        categoryService = retrofit.create(CategoryService.class);

        // Set initial state
        syncStatusLive.setValue(SynchronizationState.IDLE);
    }

    // --- Public Accessors ---

    public LiveData<SynchronizationState> getSyncStatus() {
        return syncStatusLive;
    }


    // --- Core Synchronization Logic ---

    /**
     * Finds and processes all pending synchronization logs for Categories.
     */
    public void synchronizeCategories() {
        syncStatusLive.postValue(SynchronizationState.CHECKING);
        Log.d(TAG, "Starting category sync check...");

        executorService.execute(() -> {
            try {
                // 1. Get all pending logs (PENDING, UPDATED, DELETED)
                List<SynchronizationLog> logsToSync = syncLogDAO.getAllLogs();

                if (logsToSync.isEmpty()) {
                    syncStatusLive.postValue(SynchronizationState.NO_CHANGES);
                    Log.d(TAG, "No category changes found to sync.");
                    return;
                }

                syncStatusLive.postValue(SynchronizationState.PROCESSING);
                Log.d(TAG, logsToSync.size() + " category changes found. Starting processing...");

                // 2. Process logs sequentially
                for (int i = 0; i < logsToSync.size(); i++) {
                    SynchronizationLog log = logsToSync.get(i);
                    // Calculate progress
                    int progressPercentage = (int) (((double) i / logsToSync.size()) * 100);
                    syncStatusLive.postValue(SynchronizationState.PROCESSING); // Re-post to update progress (Not implemented fully here, but good practice)
                    Log.d(TAG, "Processing log ID: " + log.getId() + " Status: " + log.getStatus());


                    if ("categories".equals(log.getTableName())) {
                        boolean result = handleCategorySync(log);
                        if (!result) {
                            // If any log fails, stop and report error
                            syncStatusLive.postValue(SynchronizationState.ERROR);
                            Log.e(TAG, "Sync failed for log ID: " + log.getId());
                            return;
                        }
                    }
                }

                // 3. If loop completes, synchronization is successful
                syncStatusLive.postValue(SynchronizationState.SUCCESS);
                Log.d(TAG, "Category synchronization complete.");

            } catch (Exception e) {
                Log.e(TAG, "Database access error during sync.", e);
                syncStatusLive.postValue(SynchronizationState.ERROR);
            }
        });
    }

    /**
     * Synchronously handles a single SynchronizationLog entry for Categories.
     * Must be called from a background thread (executorService).
     * @param log The log entry to process.
     * @return true on successful server operation and database update, false otherwise.
     */
    private boolean handleCategorySync(SynchronizationLog log) {
        try {
            // log.getRecordId() now returns the localId
            Category category = categoryDAO.getCategoryById(log.getRecordId());

            switch (log.getStatus()) {
                case "PENDING":
                    // Category must exist locally for PENDING to be valid
                    if (category != null) {
                        return addCategoryToServer(category, log);
                    } else {
                        // Edge case: Category was deleted locally before sync, just delete the log.
                        syncLogDAO.delete(log);
                        return true;
                    }

                case "UPDATED":
                    // Category must exist locally for UPDATED to be valid, and must have a server ID
                    if (category != null && category.getId() != 0) {
                        return updateCategoryOnServer(category, log);
                    } else {
                        // Cannot update a record that doesn't exist or doesn't have a server ID, delete the log.
                        syncLogDAO.delete(log);
                        return true;
                    }

                case "DELETED":
                    // The category itself is deleted, but we need the server ID from the log (log.getRecordId())
                    // Note: If 'DELETED' log is present, the category should already be deleted locally.
                    return deleteCategoryFromServer(log);

                default:
                    // Unknown or SYNCED status (shouldn't happen here), delete the log entry.
                    syncLogDAO.delete(log);
                    return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling category sync log ID: " + log.getId(), e);
            // Don't delete log if exception occurs, try again later
            return false;
        }
    }


    // --- Server Communication Methods (Synchronous Retrofit execution) ---

    /**
     * Uploads a new local category to the server (PENDING status).
     * @param localCategory The local Category object.
     * @param log The synchronization log entry.
     * @return true if successful, false otherwise.
     */
    private boolean addCategoryToServer(Category localCategory, SynchronizationLog log) {
        // Since the server needs the local_id to link back, we use localCategory for the body
        try {
            // Note: execute() is synchronous and must be called on a background thread.
            Call<Category> call = categoryService.createCategory(localCategory);
            Response<Category> response = call.execute();

            if (response.isSuccessful() && response.body() != null) {
                Category serverCategory = response.body();

                // 1. Update the local database record
                // The server Category should contain the localId, server 'id', and server-assigned data.
                localCategory.setId(serverCategory.getId()); // Update local record with server ID
                categoryDAO.update(localCategory);

                // 2. Delete the log entry
                syncLogDAO.delete(log);

                // FIX: Use getLocalId() to resolve the error
                Log.d(TAG, "Category created successfully. Local ID: " + serverCategory.getLocalId() + " -> Server ID: " + serverCategory.getId());
                return true;
            } else {
                Log.e(TAG, "Server error creating category: " + response.code());
                // Leave log entry for retry
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Network error creating category.", e);
            // Leave log entry for retry
            return false;
        }
    }

    /**
     * Updates an existing category on the server (UPDATED status).
     * @param localCategory The local Category object.
     * @param log The synchronization log entry.
     * @return true if successful, false otherwise.
     */
    private boolean updateCategoryOnServer(Category localCategory, SynchronizationLog log) {
        try {
            // We use the server ID (localCategory.getId()) for the path, and the full object for the body.
            Call<Category> call = categoryService.updateCategory(localCategory.getId(), localCategory);
            Response<Category> response = call.execute();

            if (response.isSuccessful()) {
                // 1. Delete the log entry
                syncLogDAO.delete(log);
                Log.d(TAG, "Category updated successfully. Server ID: " + localCategory.getId());
                return true;
            } else {
                Log.e(TAG, "Server error updating category: " + response.code());
                // Leave log entry for retry
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Network error updating category.", e);
            return false;
        }
    }

    /**
     * Deletes a category from the server (DELETED status).
     * @param log The synchronization log entry containing the server ID (recordId).
     * @return true if successful, false otherwise.
     */
    private boolean deleteCategoryFromServer(SynchronizationLog log) {
        try {
            // We use the server ID (log.getRecordId()) for the deletion.
            // NOTE: The log must store the SERVER ID for DELETED actions to be correct here.
            // Since the log.getRecordId() is currently storing the localId (from PENDING/UPDATED),
            // this is an issue. For now, we assume the DELETED log was created after the PENDING
            // log was successfully processed and the local entity's ID field was updated to the server ID.
            // The logic should be updated later to ensure DELETED logs store the Server ID.
            Call<Void> call = categoryService.deleteCategory(log.getRecordId());
            Response<Void> response = call.execute();

            if (response.isSuccessful() || response.code() == 404) { // Treat 404 (Not Found) as success since goal is met
                // 1. Delete the log entry
                syncLogDAO.delete(log);
                Log.d(TAG, "Category deleted successfully (or already deleted). Server ID: " + log.getRecordId());
                return true;
            } else {
                Log.e(TAG, "Server error deleting category: " + response.code());
                // Leave log entry for retry
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Network error deleting category.", e);
            return false;
        }
    }
}