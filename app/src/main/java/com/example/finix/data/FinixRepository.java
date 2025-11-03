package com.example.finix.data;

import android.app.Application;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class FinixRepository {
    private static final String LOG_TAG = "FinixRepository";

    // IMPORTANT: Replace with your actual ORDS URL
    private static final String ORDS_BASE_URL = "http://192.168.8.182:8080/ords/finix/";

    // Local Database Access
    private final CategoryDAO categoryDao;
    private final SynchronizationLogDAO syncLogDao;
    private final LiveData<List<Category>> allCategories;

    // Remote Network Access
    private final CategoryService categoryService;
    private final ExecutorService executorService;

    // LiveData references from ViewModel
    private final MutableLiveData<String> syncStatus;
    private final MutableLiveData<Integer> syncProgress;

    public FinixRepository(Application application, MutableLiveData<String> status, MutableLiveData<Integer> progress) {
        // 1. Initialize Local Database
        FinixDatabase db = FinixDatabase.getDatabase(application);
        categoryDao = db.categoryDao();
        syncLogDao = db.synchronizationLogDao();
        allCategories = categoryDao.getAllCategoriesLiveData();

        // 2. Initialize Background Executor
        executorService = Executors.newSingleThreadExecutor();

        // 3. Initialize Status/Progress LiveData
        this.syncStatus = status;
        this.syncProgress = progress;

        // 4. Initialize Retrofit for Network Calls
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ORDS_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        categoryService = retrofit.create(CategoryService.class);

        Log.i(LOG_TAG, "Repository initialized successfully. ORDS Base URL: " + ORDS_BASE_URL);
    }

    /**
     * Public accessor for LiveData from Room.
     */
    public LiveData<List<Category>> getAllCategories() {
        return allCategories;
    }

    /**
     * Initiates the synchronization process.
     */
    public void syncCategories() {
        // Start the process by pushing local changes first
        syncPendingCategoriesToRemote();
    }

    // --- MAIN SYNCHRONIZATION METHOD ---

    /**
     * Initiates the PUSH phase: sends local PENDING category INSERTS and UPDATES.
     * This method orchestrates the two distinct PUSH operations.
     */
    public void syncPendingCategoriesToRemote() {
        executorService.execute(() -> {
            Log.d(LOG_TAG, "Starting PUSH phase: Checking for local category PENDING changes (Insert/Update)...");
            syncStatus.postValue("PUSH Phase: Checking pending changes...");

            // 1. Fetch all PENDING logs
            List<SynchronizationLog> allPendingLogs = syncLogDao.getLogsByStatus("PENDING");

            // 2. Filter logs into separate lists based on ID (Temporary ID < 0 for Insert)
            List<SynchronizationLog> insertLogs = new ArrayList<>();
            List<SynchronizationLog> updateLogs = new ArrayList<>();

            for (SynchronizationLog log : allPendingLogs) {
                if ("CATEGORIES".equals(log.getTableName())) {
                    if (log.getRecordId() < 0) {
                        insertLogs.add(log);
                    } else {
                        updateLogs.add(log);
                    }
                }
            }

            int totalInserts = insertLogs.size();
            int totalUpdates = updateLogs.size();
            int totalLogs = totalInserts + totalUpdates;

            // Handle zero PUSH changes
            if (totalLogs == 0) {
                Log.d(LOG_TAG, "PUSH phase: No pending changes found. Sync complete.");
                syncProgress.postValue(100);
                syncStatus.postValue("Sync Complete: No local changes to save.");
                return;
            }

            // 3. Execute Inserts, then Updates
            // Insert is always done first to resolve temporary IDs
            boolean insertSuccess = pushInsertCategoriesToRemote(insertLogs, totalLogs);

            // Only proceed to updates if inserts completed successfully (or if there were no inserts)
            if (insertSuccess) {
                pushUpdateCategoriesToRemote(updateLogs, totalLogs, totalInserts);
            }
        });
    }

    // --- INSERT (POST) METHOD ---

    /**
     * Executes the PUSH (INSERT/POST) operation for new categories.
     * Updates local IDs and sync logs upon success.
     */
    private boolean pushInsertCategoriesToRemote(List<SynchronizationLog> insertLogs, int totalOverallLogs) {
        int processedInserts = 0;
        for (SynchronizationLog log : insertLogs) {
            processedInserts++;
            final int progress = (int) ( (processedInserts / (double) totalOverallLogs) * 100);
            syncProgress.postValue(progress);
            syncStatus.postValue("PUSH Phase: Saving new category " + processedInserts + " of " + insertLogs.size() + "...");

            try {
                Category localCategory = categoryDao.getCategoryById(log.getRecordId());

                if (localCategory == null) {
                    Log.e(LOG_TAG, "Data integrity alert: PENDING log found for non-existent category ID " + log.getRecordId() + ". Removing orphaned log.");
                    syncLogDao.delete(log);
                    continue;
                }

                CategoryService.CategoryNetworkModel networkModel = mapToNetworkModel(localCategory);

                // POST (Insert)
                Response<CategoryService.CategoryNetworkModel> response = categoryService.createCategory(networkModel).execute();

                if (response.isSuccessful() && response.body() != null) {
                    Integer newRemoteId = response.body().getId();

                    if (newRemoteId != null && newRemoteId > 0) {
                        Log.i(LOG_TAG, "INSERT PUSH successful. Updating local ID from " + localCategory.getId() + " to " + newRemoteId);

                        // 1. Update the local category with the permanent ID
                        localCategory.setId(newRemoteId);
                        categoryDao.update(localCategory);

                        // 2. Update the sync log entry to use the permanent ID and mark SYNCED
                        log.setRecordId(newRemoteId);
                        log.setStatus("SYNCED");
                        log.setLastSyncedTimestamp(System.currentTimeMillis());
                        syncLogDao.update(log);
                    } else {
                        Log.e(LOG_TAG, "POST successful but server returned invalid new ID: " + newRemoteId + ". Keeping PENDING for retry.");
                    }
                } else {
                    Log.e(LOG_TAG, "INSERT PUSH failed for category: " + localCategory.getName() + ". Code: " + response.code() + ". Keeping PENDING.");
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "FATAL Network/Execution error during INSERT PUSH. Aborting Sync: " + e.getMessage(), e);
                syncStatus.postValue("Sync Failed: Network error during INSERT PUSH.");
                syncProgress.postValue(100);
                return false; // Indicate failure
            }
        }
        return true; // Indicate success
    }

    // --- UPDATE (PUT) METHOD ---

    /**
     * Executes the PUSH (UPDATE/PUT) operation for existing categories.
     */
    private void pushUpdateCategoriesToRemote(List<SynchronizationLog> updateLogs, int totalOverallLogs, int insertsProcessed) {
        int processedUpdates = 0;
        for (SynchronizationLog log : updateLogs) {
            processedUpdates++;
            // Calculate progress bar relative to the total number of logs (inserts + updates)
            final int progress = (int) ( ((insertsProcessed + processedUpdates) / (double) totalOverallLogs) * 100);
            syncProgress.postValue(progress);
            syncStatus.postValue("PUSH Phase: Updating category " + processedUpdates + " of " + updateLogs.size() + "...");

            try {
                Category localCategory = categoryDao.getCategoryById(log.getRecordId());

                if (localCategory == null) {
                    Log.e(LOG_TAG, "Data integrity alert: PENDING log found for non-existent category ID " + log.getRecordId() + ". Removing orphaned log.");
                    syncLogDao.delete(log);
                    continue;
                }

                CategoryService.CategoryNetworkModel networkModel = mapToNetworkModel(localCategory);

                // PUT (Update)
                Log.d(LOG_TAG, "UPDATE PUSH: PUTing updated category ID: " + localCategory.getId());
                Response<CategoryService.CategoryNetworkModel> response = categoryService.updateCategory(localCategory.getId(), networkModel).execute();

                if (response.isSuccessful()) {
                    Log.d(LOG_TAG, "UPDATE PUSH successful for ID " + localCategory.getId() + ". Marking log SYNCED.");
                    log.setStatus("SYNCED");
                    log.setLastSyncedTimestamp(System.currentTimeMillis());
                    syncLogDao.update(log);
                } else {
                    Log.e(LOG_TAG, "UPDATE PUSH failed for ID " + localCategory.getId() + ". Code: " + response.code() + ". Keeping PENDING.");
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "FATAL Network/Execution error during UPDATE PUSH. Aborting Sync: " + e.getMessage(), e);
                syncStatus.postValue("Sync Failed: Network error during UPDATE PUSH.");
                syncProgress.postValue(100);
                return;
            }
        }

        // Final success status
        Log.d(LOG_TAG, "PUSH phase complete. All " + totalOverallLogs + " logs processed.");
        syncProgress.postValue(100);
        syncStatus.postValue("Sync Complete");
    }

    // --- Helper function to map local entity to network model (for PUSH) ---
    private CategoryService.CategoryNetworkModel mapToNetworkModel(Category localCategory) {
        // Only include the fields the server needs to process the PUSH action
        return new CategoryService.CategoryNetworkModel(localCategory.getName());
    }
}