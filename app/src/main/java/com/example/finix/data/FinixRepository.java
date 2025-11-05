package com.example.finix.data;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    public void synchronizeCategories() {
        syncStatusLive.postValue(SynchronizationState.CHECKING);
        Log.i(TAG, "--- Starting synchronizeCategories() ---");

        executorService.execute(() -> {
            try {
                List<SynchronizationLog> logsToSync = syncLogDAO.getAllLogs();
                if (logsToSync.isEmpty()) {
                    syncStatusLive.postValue(SynchronizationState.NO_CHANGES);
                    return;
                }

                syncStatusLive.postValue(SynchronizationState.PROCESSING);

                for (SynchronizationLog log : logsToSync) {
                    if ("categories".equals(log.getTableName())) {
                        boolean result = handleCategorySync(log);
                        if (!result) {
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

            switch (log.getStatus()) {
                case "PENDING":
                    if (category != null) return addCategoryToServer(category, log);
                    syncLogDAO.delete(log);
                    return true;

                case "UPDATED":
                    if (category != null && category.getId() != 0) return updateCategoryOnServer(category, log);
                    syncLogDAO.delete(log);
                    return true;

                case "DELETED":
                    return deleteCategoryFromServer(log);

                default:
                    syncLogDAO.delete(log);
                    return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in handleCategorySync for log ID: " + log.getId(), e);
            return false;
        }
    }

    private boolean addCategoryToServer(Category localCategory, SynchronizationLog log) {
        Log.i(TAG, "-> addCategoryToServer. Local Category JSON: " + gson.toJson(localCategory));

        try {
            Call<ResponseBody> call = categoryService.createCategory(localCategory);
            Response<ResponseBody> response = call.execute();

            if (!response.isSuccessful()) {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                Log.e(TAG, "Server error creating category. Code: " + response.code() + ". Error Body: " + errorBody);
                return false;
            }

            String rawJsonBody = response.body() != null ? response.body().string() : "";

            if (rawJsonBody.isEmpty()) {
                Log.w(TAG, "Empty response body. Deleting log.");
                syncLogDAO.delete(log);
                return true;
            }

            Log.i(TAG, "SUCCESSFUL RESPONSE BODY (RAW): " + rawJsonBody);

            // Parse JSON safely
            try {
                CategoryResponse categoryResponse = gson.fromJson(rawJsonBody, CategoryResponse.class);

                if (categoryResponse == null || categoryResponse.data == null) {
                    Log.e(TAG, "Server returned invalid category data.");
                    return false;
                }

                Category serverCategory = categoryResponse.data;

                // ⚡ ONLY update the server ID in local DB
                localCategory.setId(serverCategory.getId());
                categoryDAO.update(localCategory);

                syncLogDAO.delete(log);
                Log.i(TAG, "Category synced. Server ID: " + serverCategory.getId() + ", local_id remains: " + localCategory.getLocalId());

                return true;

            } catch (Exception ex) {
                Log.e(TAG, "JSON parsing error. Raw JSON: " + rawJsonBody, ex);
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Network error creating category.", e);
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
