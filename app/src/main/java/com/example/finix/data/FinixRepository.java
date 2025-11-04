package com.example.finix.data;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.ResponseBody; // Import required
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import com.google.gson.Gson;

public class FinixRepository {

    private static final String TAG = "FinixRepository_LOG";
    private static final String BASE_URL = "http://192.168.8.182:8080/ords/";

    private final CategoryDAO categoryDAO;
    private final SynchronizationLogDAO syncLogDAO;
    private final CategoryService categoryService;
    private final ExecutorService executorService;
    private final Gson gson;

    // LiveData to expose synchronization status updates
    private final MutableLiveData<SynchronizationState> syncStatusLive = new MutableLiveData<>();

    public enum SynchronizationState {
        IDLE, CHECKING, PROCESSING, SUCCESS, ERROR, NO_CHANGES
    }

    public FinixRepository(Context context) {
        Log.i(TAG, "FinixRepository initializing...");

        FinixDatabase db = FinixDatabase.getDatabase(context);
        categoryDAO = db.categoryDao();
        syncLogDAO = db.synchronizationLogDao();
        Log.d(TAG, "Database and DAOs initialized.");

        executorService = Executors.newFixedThreadPool(4);
        Log.d(TAG, "ExecutorService initialized with 4 threads.");

        gson = new Gson();
        Log.d(TAG, "Gson instance created for logging.");

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        categoryService = retrofit.create(CategoryService.class);
        Log.d(TAG, "Retrofit initialized with BASE_URL: " + BASE_URL);

        syncStatusLive.setValue(SynchronizationState.IDLE);
        Log.i(TAG, "FinixRepository initialization complete. Status: IDLE");
    }

    // --- Public Accessors ---

    public LiveData<SynchronizationState> getSyncStatus() {
        Log.v(TAG, "getSyncStatus() called.");
        return syncStatusLive;
    }

    // --- Core Synchronization Logic ---

    public void synchronizeCategories() {
        syncStatusLive.postValue(SynchronizationState.CHECKING);
        Log.i(TAG, "--- Starting synchronizeCategories() ---");

        executorService.execute(() -> {
            try {
                Log.d(TAG, "Running sync on background thread: " + Thread.currentThread().getName());

                List<SynchronizationLog> logsToSync = syncLogDAO.getAllLogs();
                Log.v(TAG, "DB query for logsToSync completed. Found " + logsToSync.size() + " logs.");

                if (logsToSync.isEmpty()) {
                    syncStatusLive.postValue(SynchronizationState.NO_CHANGES);
                    Log.i(TAG, "No category changes found to sync. Status: NO_CHANGES");
                    return;
                }

                syncStatusLive.postValue(SynchronizationState.PROCESSING);
                Log.i(TAG, logsToSync.size() + " category changes found. Starting processing...");

                for (int i = 0; i < logsToSync.size(); i++) {
                    SynchronizationLog log = logsToSync.get(i);
                    int progressPercentage = (int) (((double) i / logsToSync.size()) * 100);
                    Log.i(TAG, "Processing log " + (i + 1) + "/" + logsToSync.size() + " (" + progressPercentage + "%). Log ID: " + log.getId() + ", Status: " + log.getStatus() + ", Table: " + log.getTableName() + ", Record ID: " + log.getRecordId());

                    if ("categories".equals(log.getTableName())) {
                        boolean result = handleCategorySync(log);
                        if (!result) {
                            syncStatusLive.postValue(SynchronizationState.ERROR);
                            Log.e(TAG, "Sync FAILED for log ID: " + log.getId() + ". Stopping sync process.");
                            return;
                        }
                    } else {
                        Log.w(TAG, "Skipping log ID: " + log.getId() + ". Unknown table: " + log.getTableName());
                    }
                }

                syncStatusLive.postValue(SynchronizationState.SUCCESS);
                Log.i(TAG, "Category synchronization successful. Status: SUCCESS.");

            } catch (Exception e) {
                Log.e(TAG, "FATAL: Database access error during sync (Top-level catch).", e);
                syncStatusLive.postValue(SynchronizationState.ERROR);
            }
        });
    }

    private boolean handleCategorySync(SynchronizationLog log) {
        Log.d(TAG, "-> handleCategorySync for Log ID: " + log.getId() + ", Status: " + log.getStatus());
        try {
            int localRecordId = log.getRecordId();
            Category category = categoryDAO.getCategoryById(localRecordId);

            Log.v(TAG, "DB fetch for category with local_id: " + localRecordId + " completed. Category found: " + (category != null));

            switch (log.getStatus()) {
                case "PENDING":
                    Log.d(TAG, "Case PENDING. Local category: " + (category != null ? category.getName() : "NULL"));
                    if (category != null) {
                        return addCategoryToServer(category, log);
                    } else {
                        Log.w(TAG, "PENDING log found, but local category is NULL. Deleting log ID: " + log.getId());
                        syncLogDAO.delete(log);
                        return true;
                    }

                case "UPDATED":
                    Log.d(TAG, "Case UPDATED. Category name: " + (category != null ? category.getName() : "NULL") + ", Server ID: " + (category != null ? category.getId() : 0));
                    if (category != null && category.getId() != 0) {
                        return updateCategoryOnServer(category, log);
                    } else {
                        Log.w(TAG, "UPDATED log found, but category is missing or has no server ID. Deleting log ID: " + log.getId());
                        syncLogDAO.delete(log);
                        return true;
                    }

                case "DELETED":
                    Log.d(TAG, "Case DELETED. Attempting to delete server ID: " + localRecordId);
                    return deleteCategoryFromServer(log);

                default:
                    Log.w(TAG, "Unknown log status: " + log.getStatus() + ". Deleting log ID: " + log.getId());
                    syncLogDAO.delete(log);
                    return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in handleCategorySync for log ID: " + log.getId(), e);
            return false;
        }
    }


    // --- Server Communication Methods (Synchronous Retrofit execution) ---

    /**
     * Uploads a new local category to the server (PENDING status).
     * Now handles Call<ResponseBody> to be resilient against empty bodies.
     */
    private boolean addCategoryToServer(Category localCategory, SynchronizationLog log) {
        Log.i(TAG, "-> addCategoryToServer. Local Category JSON being sent: " + gson.toJson(localCategory));

        try {
            // NOTE: Return type is now ResponseBody
            Call<ResponseBody> call = categoryService.createCategory(localCategory);
            Log.d(TAG, "Executing call: " + call.request().url());

            Response<ResponseBody> response = call.execute();

            Log.d(TAG, "Network response received. Code: " + response.code() + ", Message: " + response.message());

            if (response.isSuccessful()) {

                String rawJsonBody = "";
                if (response.body() != null) {
                    rawJsonBody = response.body().string();
                }

                // Check for empty body (the cause of EOFException)
                if (rawJsonBody.isEmpty()) {
                    // This is the fallback if the server unexpectedly returns a successful but empty response
                    Log.w(TAG, "Successful response (" + response.code() + ") received but body was empty. Treating as success and deleting log, but local category ID not updated.");
                    syncLogDAO.delete(log);
                    return true;
                }

                Log.i(TAG, "SUCCESSFUL RESPONSE BODY (RAW): " + rawJsonBody);

                // Manually parse the JSON now that we know it's not empty
                Category serverCategory = gson.fromJson(rawJsonBody, Category.class);

                if (serverCategory != null) {
                    Log.d(TAG, "Server Category Body parsed successfully. Server ID: " + serverCategory.getId() + ", Local ID from Server: " + serverCategory.getLocalId());

                    // 1. Update the local database record
                    localCategory.setId(serverCategory.getId()); // Update local record with server ID
                    categoryDAO.update(localCategory);
                    Log.v(TAG, "Local DB Category updated with Server ID: " + serverCategory.getId());

                    // 2. Delete the log entry
                    syncLogDAO.delete(log);
                    Log.i(TAG, "Category created successfully. Log ID " + log.getId() + " deleted.");
                    return true;
                } else {
                    Log.e(TAG, "Server returned success (" + response.code() + ") but response body failed to parse into Category object.");
                    return false;
                }
            } else {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                Log.e(TAG, "Server error creating category. Code: " + response.code() + ". Error Body: " + errorBody);
                return false;
            }
        } catch (Exception e) {
            // This now catches true network errors or parsing exceptions on non-empty bodies
            Log.e(TAG, "FATAL: Network error creating category (addCategoryToServer).", e);
            Log.e(TAG, "ERROR CLASS: " + e.getClass().getName() + ". MESSAGE: " + e.getMessage());
            return false;
        }
    }

    /**
     * Updates an existing category on the server (UPDATED status).
     */
    private boolean updateCategoryOnServer(Category localCategory, SynchronizationLog log) {
        Log.i(TAG, "-> updateCategoryOnServer. Server ID: " + localCategory.getId() + ". JSON being sent: " + gson.toJson(localCategory));

        try {
            Call<Category> call = categoryService.updateCategory(localCategory.getId(), localCategory);
            Response<Category> response = call.execute();

            Log.d(TAG, "Update response received. Code: " + response.code());

            if (response.isSuccessful()) {
                syncLogDAO.delete(log);
                Log.i(TAG, "Category updated successfully. Log ID " + log.getId() + " deleted.");
                return true;
            } else {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                Log.e(TAG, "Server error updating category. Code: " + response.code() + ". Error Body: " + errorBody);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Network error updating category.", e);
            return false;
        }
    }

    /**
     * Deletes a category from the server (DELETED status).
     */
    private boolean deleteCategoryFromServer(SynchronizationLog log) {
        Log.i(TAG, "-> deleteCategoryFromServer. Deleting record ID: " + log.getRecordId());

        try {
            Call<Void> call = categoryService.deleteCategory(log.getRecordId());
            Response<Void> response = call.execute();

            Log.d(TAG, "Delete response received. Code: " + response.code());

            if (response.isSuccessful() || response.code() == 404) {
                syncLogDAO.delete(log);
                Log.i(TAG, "Category deleted successfully (or already deleted). Log ID " + log.getId() + " deleted.");
                return true;
            } else {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                Log.e(TAG, "Server error deleting category. Code: " + response.code() + ". Error Body: " + errorBody);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Network error deleting category.", e);
            return false;
        }
    }
}