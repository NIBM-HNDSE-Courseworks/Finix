package com.example.finix.data;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import com.google.gson.Gson;

// --- Wrapper class for ORDS JSON response ---
class CategoryResponse {
    public String status;
    public String message;
    public Category data; // the actual category object
}

public class FinixRepository {

    private static final String TAG = "FinixRepository_LOG";
    private static final String BASE_URL = "http://192.168.8.182:8080/ords/";

    private final CategoryDAO categoryDAO;
    private final SynchronizationLogDAO syncLogDAO;
    private final CategoryService categoryService;
    private final ExecutorService executorService;
    private final Gson gson;

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

    public LiveData<SynchronizationState> getSyncStatus() {
        Log.v(TAG, "getSyncStatus() called.");
        return syncStatusLive;
    }

    public List<SynchronizationLog> getAllSynchronizationLogs() {
        Log.v(TAG, "getAllSynchronizationLogs() called. Accessing DB off-main thread.");

        // Use Callable and Future to retrieve a value from the background thread
        Callable<List<SynchronizationLog>> callable = () -> syncLogDAO.getAllLogs();

        Future<List<SynchronizationLog>> future = executorService.submit(callable);

        try {
            // Wait for the result
            return future.get();
        } catch (Exception e) {
            Log.e(TAG, "Error fetching synchronization logs.", e);
            return new java.util.ArrayList<>();
        }
    }


    public void synchronizeCategories() {
        syncStatusLive.postValue(SynchronizationState.CHECKING);
        Log.i(TAG, "--- Starting synchronizeCategories() ---");

        executorService.execute(() -> {
            try {
                // Ensure DAO access is on the background thread
                List<SynchronizationLog> logsToSync = syncLogDAO.getAllLogs();

                if (logsToSync.isEmpty()) {
                    syncStatusLive.postValue(SynchronizationState.NO_CHANGES);
                    return;
                }

                // ✅ Sort logs by local_id of their category
                logsToSync.sort((log1, log2) -> {
                    Category c1 = categoryDAO.getCategoryById(log1.getRecordId());
                    Category c2 = categoryDAO.getCategoryById(log2.getRecordId());
                    int id1 = c1 != null ? c1.getLocalId() : Integer.MAX_VALUE;
                    int id2 = c2 != null ? c2.getLocalId() : Integer.MAX_VALUE;
                    return Integer.compare(id1, id2);
                });

                syncStatusLive.postValue(SynchronizationState.PROCESSING);

                for (SynchronizationLog log : logsToSync) {
                    if ("categories".equals(log.getTableName())) {
                        boolean result = handleCategorySync(log);
                        if (!result) {
                            // Stop sync and mark overall state as ERROR
                            syncStatusLive.postValue(SynchronizationState.ERROR);
                            return;
                        }
                    }
                }

                syncStatusLive.postValue(SynchronizationState.SUCCESS);

            } catch (Exception e) {
                Log.e(TAG, "FATAL: Database access error during sync.", e);
                syncStatusLive.postValue(SynchronizationState.ERROR);
            }
        });
    }


    private boolean handleCategorySync(SynchronizationLog log) {
        try {
            int localRecordId = log.getRecordId();
            Category category = categoryDAO.getCategoryById(localRecordId);

            // Determine the category name for logging/messaging
            String categoryName = category != null ? category.getName() : "Unknown Category (ID: " + localRecordId + ")";

            switch (log.getStatus()) {
                case "PENDING":
                    if (category != null) return addCategoryToServer(category, log, categoryName);

                    // Category was deleted locally before sync could run (stale log)
                    log.setStatus("SYNCED - STALE");
                    log.setMessage("Category record not found locally (ID: " + localRecordId + "). Log entry cleared.");
                    syncLogDAO.update(log); // Update and clear the log for user reference
                    return true;

                case "UPDATED":
                    if (category != null && category.getId() != 0) return updateCategoryOnServer(category, log, categoryName);

                    // Update failed because category was either deleted or never synced (id=0). Treat as stale/cleared.
                    log.setStatus("SYNCED - STALE");
                    log.setMessage("Update skipped: Category record not found locally or has no server ID (ID: " + localRecordId + ").");
                    syncLogDAO.update(log);
                    return true;

                case "DELETED":
                    return deleteCategoryFromServer(log, categoryName);

                default:
                    // Should not happen, but clear if it does.
                    log.setStatus("SYNCED - STALE");
                    log.setMessage("Log with unknown status '" + log.getStatus() + "' found. Log entry cleared.");
                    syncLogDAO.update(log);
                    return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in handleCategorySync for log ID: " + log.getId(), e);
            // On internal error, update log to ERROR for review
            log.setStatus("ERROR");
            log.setMessage("Internal error processing sync for category: " + e.getMessage());
            syncLogDAO.update(log);
            return false;
        }
    }

    private boolean addCategoryToServer(Category localCategory, SynchronizationLog log, String categoryName) {
        Log.i(TAG, "-> addCategoryToServer. Local Category JSON: " + gson.toJson(localCategory));

        try {
            Call<ResponseBody> call = categoryService.createCategory(localCategory);
            Response<ResponseBody> response = call.execute();

            if (!response.isSuccessful()) {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                Log.e(TAG, "Server error creating category. Code: " + response.code() + ". Error Body: " + errorBody);

                // ❌ Update log on Server Error
                log.setStatus("ERROR - SERVER");
                log.setMessage("ADD FAILED for '" + categoryName + "'. Code: " + response.code() + ". Error: " + errorBody);
                syncLogDAO.update(log);
                return false;
            }

            String rawJsonBody = response.body() != null ? response.body().string() : "";

            if (rawJsonBody.isEmpty()) {
                Log.w(TAG, "Empty response body. Log updated to error state.");

                // ❌ Update log on Empty Response
                log.setStatus("ERROR - RESPONSE");
                log.setMessage("ADD FAILED for '" + categoryName + "'. Empty response body from server.");
                syncLogDAO.update(log);
                return true; // Treat as non-critical fail, continue sync process
            }

            Log.i(TAG, "SUCCESSFUL RESPONSE BODY (RAW): " + rawJsonBody);

            // Parse JSON safely
            try {
                CategoryResponse categoryResponse = gson.fromJson(rawJsonBody, CategoryResponse.class);

                if (categoryResponse == null || categoryResponse.data == null) {
                    Log.e(TAG, "Server returned invalid category data.");

                    // ❌ Update log on JSON/Data Error
                    log.setStatus("ERROR - DATA");
                    log.setMessage("ADD FAILED for '" + categoryName + "'. Server returned invalid data/JSON.");
                    syncLogDAO.update(log);
                    return false;
                }

                Category serverCategory = categoryResponse.data;

                // ⚡ ONLY update the server ID in local DB
                localCategory.setId(serverCategory.getId());
                categoryDAO.update(localCategory);

                // ✅ Update log on SUCCESS
                log.setStatus("SYNCED - ADDED");
                log.setMessage("Category '" + categoryName + "' added successfully. Server ID: " + serverCategory.getId());
                log.setRecordId(serverCategory.getId()); // Update log record ID to server ID for DELETED logs later
                syncLogDAO.update(log);

                Log.i(TAG, "Category synced. Server ID: " + serverCategory.getId() + ", local_id remains: " + localCategory.getLocalId());

                return true;

            } catch (Exception ex) {
                Log.e(TAG, "JSON parsing error. Raw JSON: " + rawJsonBody, ex);

                // ❌ Update log on JSON Parsing Error
                log.setStatus("ERROR - JSON");
                log.setMessage("ADD FAILED for '" + categoryName + "'. JSON parsing failed: " + ex.getMessage());
                syncLogDAO.update(log);
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Network error creating category.", e);

            // ❌ Update log on Network Error
            log.setStatus("ERROR - NETWORK");
            log.setMessage("ADD FAILED for '" + categoryName + "'. Network error: " + e.getMessage());
            syncLogDAO.update(log);
            return false;
        }
    }

    /**
     * Updates an existing category on the server (UPDATED status).
     */
    private boolean updateCategoryOnServer(Category localCategory, SynchronizationLog log, String categoryName) {
        Log.i(TAG, "-> updateCategoryOnServer. Server ID: " + localCategory.getId() + ". JSON being sent: " + gson.toJson(localCategory));

        try {
            // Change to ResponseBody so we can log raw JSON
            Call<ResponseBody> call = categoryService.updateCategory(localCategory.getId(), localCategory);
            Response<ResponseBody> response = call.execute();

            Log.d(TAG, "Update response received. Code: " + response.code());

            // ✅ Log raw JSON from response body (if any)
            String rawJsonBody = response.body() != null ? response.body().string() : "";
            if (!rawJsonBody.isEmpty()) {
                Log.i(TAG, "UPDATE RESPONSE BODY (RAW): " + rawJsonBody);
            }

            if (response.isSuccessful()) {
                // ✅ Update log on SUCCESS
                log.setStatus("SYNCED - UPDATED");
                log.setMessage("Category '" + categoryName + "' (ID: " + localCategory.getId() + ") updated successfully.");
                syncLogDAO.update(log);

                Log.i(TAG, "Category updated successfully. Log ID " + log.getId() + " updated.");
                return true;
            } else {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                Log.e(TAG, "Server error updating category. Code: " + response.code() + ". Error Body: " + errorBody);

                // ❌ Update log on Server Error
                log.setStatus("ERROR - SERVER");
                log.setMessage("UPDATE FAILED for '" + categoryName + "'. Code: " + response.code() + ". Error: " + errorBody);
                syncLogDAO.update(log);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Network error updating category.", e);

            // ❌ Update log on Network Error
            log.setStatus("ERROR - NETWORK");
            log.setMessage("UPDATE FAILED for '" + categoryName + "'. Network error: " + e.getMessage());
            syncLogDAO.update(log);
            return false;
        }
    }


    /**
     * Deletes a category from the server (DELETED status).
     */
    private boolean deleteCategoryFromServer(SynchronizationLog log, String categoryName) {
        int serverRecordId = log.getRecordId();
        Log.i(TAG, "-> deleteCategoryFromServer. Deleting record ID: " + serverRecordId);

        try {
            // Change to ResponseBody to log raw JSON response
            Call<ResponseBody> call = categoryService.deleteCategory(serverRecordId);
            Response<ResponseBody> response = call.execute();

            Log.d(TAG, "Delete response received. Code: " + response.code());

            // ✅ Log raw JSON from response body (if any)
            String rawJsonBody = response.body() != null ? response.body().string() : "";
            if (!rawJsonBody.isEmpty()) {
                Log.i(TAG, "DELETE RESPONSE BODY (RAW): " + rawJsonBody);
            }

            if (response.isSuccessful() || response.code() == 404) {
                // ✅ Update log on SUCCESS (404 means it's already gone, which is success for a DELETED log)
                log.setStatus("SYNCED - DELETED");
                log.setMessage("Category ID " + serverRecordId + " deleted successfully from server (or already gone).");
                syncLogDAO.update(log);

                Log.i(TAG, "Category deleted successfully (or already deleted). Log ID " + log.getId() + " updated.");
                return true;
            } else {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                Log.e(TAG, "Server error deleting category. Code: " + response.code() + ". Error Body: " + errorBody);

                // ❌ Update log on Server Error
                log.setStatus("ERROR - SERVER");
                log.setMessage("DELETE FAILED for ID " + serverRecordId + ". Code: " + response.code() + ". Error: " + errorBody);
                syncLogDAO.update(log);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Network error deleting category.", e);

            // ❌ Update log on Network Error
            log.setStatus("ERROR - NETWORK");
            log.setMessage("DELETE FAILED for ID " + serverRecordId + ". Network error: " + e.getMessage());
            syncLogDAO.update(log);
            return false;
        }
    }

}