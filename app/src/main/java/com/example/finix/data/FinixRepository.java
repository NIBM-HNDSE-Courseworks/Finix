package com.example.finix.data;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
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



// ADD these imports at the top of the file
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import com.google.gson.reflect.TypeToken; // Needed for Gson to deserialize Lists
import java.lang.reflect.Type; // Needed for TypeToken

// --- Wrapper class for ORDS JSON response ---
class CategoryResponse {
    public String status;
    public String message;
    public Category data; // the actual category object
}

class TransactionResponse {
    public String status;
    public String message;
    public Transaction data; // the actual transaction object
}

// Add these new wrapper class definitions at the top, along with CategoryResponse and TransactionResponse
class BudgetResponse {
    public String status;
    public String message;
    public Budget data; // the actual budget object
}

// --- Wrapper class for ORDS JSON response ---
class SavingsGoalResponse {
    public String status;
    public String message;
    public SavingsGoal data; // the actual savings goal object
}

// --- Wrapper class for ORDS JSON collection response (List of Synchronization Logs) ---
class SynchronizationLogsResponse {
    // The "items" field holds the array of logs returned by the ORDS collection handler
    public List<SynchronizationLog> items;
}

// Wrapper class for ORDS JSON response when creating a single SynchronizationLog
class SynchronizationLogResponse {
    public String status;
    public String message;
    // 'data' holds the actual SynchronizationLog object with the new server ID
    public SynchronizationLog data;
}
public class FinixRepository {

    private static final String TAG = "FinixRepository_LOG";
    private static final String BASE_URL = "http://192.168.32.1:8080/ords/";

    private final Context context; // <--- ADD THIS LINE
    private final ContentResolver contentResolver; // NEW: ContentResolver instance

    private final CategoryDAO categoryDAO;
    private final TransactionDAO transactionDAO; // NEW
    private final BudgetDAO budgetDAO; // NEW
    private final SavingsGoalDAO savingsGoalDAO; // NEW
    private final SynchronizationLogService synchronizationLogService; // NEW

    private final SynchronizationLogDAO syncLogDAO;
    private final CategoryService categoryService;
    private final TransactionService transactionService; // NEW
    private final BudgetService budgetService; // NEW
    private final SavingsGoalService savingsGoalService; // NEW

    private final ExecutorService executorService;
    private final Gson gson;

    private final MutableLiveData<SynchronizationState> syncStatusLive = new MutableLiveData<>();








    /**
     * Public entry point to export all database data to a file selected via SAF folder URI.
     * @param folderUriString The URI of the folder where the backup should be saved.
     * @param fileName The desired name for the backup file.
     * @return true if the backup was created successfully, false otherwise.
     */
    public boolean exportDataToBackup(String folderUriString, String fileName) {
        Log.i(TAG, "Starting database export to folder URI: " + folderUriString + ", file: " + fileName);
        Uri folderUri = Uri.parse(folderUriString);

        Callable<Boolean> callable = () -> {
            return createBackupFile(folderUri, fileName);
        };

        Future<Boolean> future = executorService.submit(callable);
        try {
            return future.get(); // Wait for the result
        } catch (Exception e) {
            Log.e(TAG, "Error waiting for backup result.", e);
            return false;
        }
    }

    /**
     * Public entry point to import all database data from a file selected via SAF file URI.
     * @param fileUriString The URI of the backup file.
     * @return true if the data was restored successfully, false otherwise.
     */
    public boolean importDataFromBackup(String fileUriString) {
        Log.i(TAG, "Starting database import from file URI: " + fileUriString);
        Uri fileUri = Uri.parse(fileUriString);

        Callable<Boolean> callable = () -> {
            return restoreDataFromFile(fileUri);
        };

        Future<Boolean> future = executorService.submit(callable);
        try {
            return future.get(); // Wait for the result
        } catch (Exception e) {
            Log.e(TAG, "Error waiting for restore result.", e);
            return false;
        }
    }


    /**
     * Private method to serialize all tables and write to a new file using ContentResolver/SAF.
     *
     * @param folderUri The URI of the folder to create the file in. (This is a Tree URI)
     * @param fileName The name of the new file.
     */
    private boolean createBackupFile(Uri folderUri, String fileName) {
        Uri fileUri = null;
        try {
            // 1. Fetch all data from DAOs
            List<Category> categories = categoryDAO.getAllCategoriesForBackup();
            List<Transaction> transactions = transactionDAO.getAllTransactionsForBackup();
            List<Budget> budgets = budgetDAO.getAllBudgetsForBackup();
            List<SavingsGoal> savingsGoals = savingsGoalDAO.getAllGoalsForBackup();
            List<SynchronizationLog> syncLogs = syncLogDAO.getAllLogs();

            // 2. Create the data structure
            java.util.Map<String, Object> backupData = new java.util.HashMap<>();
            backupData.put("categories", categories);
            backupData.put("transactions", transactions);
            backupData.put("budgets", budgets);
            backupData.put("savings_goals", savingsGoals);
            backupData.put("sync_logs", syncLogs);

            // 3. Serialize the entire structure to JSON
            String jsonString = gson.toJson(backupData);

            // 4. CRITICAL: Use DocumentsContract and ContentResolver to create and write the file

            // FIX START: Convert the Tree URI into the Document URI required by createDocument()
            // 4a. Get the Document ID from the Tree URI (e.g., "primary:Finix")
            final String documentId = DocumentsContract.getTreeDocumentId(folderUri);

            // 4b. Build the actual Document URI for the parent folder using the ID
            final Uri parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId);
            // FIX END

            // Create a new document in the selected folder URI with MIME type "application/json"
            fileUri = DocumentsContract.createDocument(contentResolver, parentDocumentUri, "application/json", fileName);

            if (fileUri == null) {
                Log.e(TAG, "Failed to create document in selected folder.");
                return false;
            }

            try (OutputStream outputStream = contentResolver.openOutputStream(fileUri)) {
                if (outputStream == null) {
                    Log.e(TAG, "Could not open output stream for file URI: " + fileUri);
                    return false;
                }
                outputStream.write(jsonString.getBytes());
            }

            Log.i(TAG, "Backup successfully created at URI: " + fileUri);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "FATAL: Error creating backup file via SAF.", e);
            return false;
        }
    }

    /**
     * Private method to read data from a file URI and restore it to the database.
     *
     * @param fileUri The URI of the backup file.
     */
    private boolean restoreDataFromFile(Uri fileUri) {
        try {
            // 1. CRITICAL: Use ContentResolver to read file content from URI
            StringBuilder jsonContent = new StringBuilder();
            try (InputStream inputStream = contentResolver.openInputStream(fileUri);
                 // Use InputStreamReader and BufferedReader to read the content efficiently
                 BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(inputStream))) {

                if (inputStream == null) {
                    Log.e(TAG, "Restore failed: Could not open input stream for URI: " + fileUri);
                    return false;
                }

                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line);
                }
            }

            // 2. Deserialize the JSON string back into the Map structure
            Type type = new TypeToken<java.util.Map<String, List<Object>>>() {}.getType();
            java.util.Map<String, List<Object>> dataMap = gson.fromJson(jsonContent.toString(), type);

            if (dataMap == null) {
                Log.e(TAG, "Restore failed: Could not parse JSON data.");
                return false;
            }

            // 3. CRITICAL: Delete existing data and insert new data within a transaction
            FinixDatabase.getDatabase(context).runInTransaction(() -> {
                Log.w(TAG, "Starting destructive database import transaction...");

                // --- FIX: Delete ALL current data in REVERSE dependency order ---
                // Tables with foreign keys must be deleted first (e.g., Transaction -> Category)
                transactionDAO.deleteAll();
                budgetDAO.deleteAll();
                savingsGoalDAO.deleteAll();
                syncLogDAO.deleteAll();
                // Parent tables are deleted last
                categoryDAO.deleteAll();
                // --- END FIX ---


                // Insert restored data (Deserialization requires a second pass with the correct Type)

                // --- FIX: Insert restored data in CORRECT dependency order ---

                // Categories (Parent table - must be inserted first)
                Type catListType = new TypeToken<List<Category>>() {}.getType();
                List<Category> categories = gson.fromJson(gson.toJson(dataMap.get("categories")), catListType);
                if (categories != null) categoryDAO.insertAll(categories);

                // Transactions (Child table, depends on Category)
                Type tranListType = new TypeToken<List<Transaction>>() {}.getType();
                List<Transaction> transactions = gson.fromJson(gson.toJson(dataMap.get("transactions")), tranListType);
                if (transactions != null) transactionDAO.insertAll(transactions);

                // Budgets (Child table, likely depends on Category/Transaction)
                Type budListType = new TypeToken<List<Budget>>() {}.getType();
                List<Budget> budgets = gson.fromJson(gson.toJson(dataMap.get("budgets")), budListType);
                if (budgets != null) budgetDAO.insertAll(budgets);

                // SavingsGoals (Order depends on schema, usually independent or child)
                Type goalListType = new TypeToken<List<SavingsGoal>>() {}.getType();
                List<SavingsGoal> savingsGoals = gson.fromJson(gson.toJson(dataMap.get("savings_goals")), goalListType);
                if (savingsGoals != null) savingsGoalDAO.insertAll(savingsGoals);

                // SyncLogs (Generally independent)
                Type logListType = new TypeToken<List<SynchronizationLog>>() {}.getType();
                List<SynchronizationLog> syncLogs = gson.fromJson(gson.toJson(dataMap.get("sync_logs")), logListType);
                if (syncLogs != null) syncLogDAO.insertAll(syncLogs);

                // --- END FIX ---

                Log.i(TAG, "Database import transaction successfully completed.");
            });

            Log.i(TAG, "Restore completed successfully from URI: " + fileUri);
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Restore failed: File I/O error using ContentResolver.", e);
            return false;
        } catch (Exception e) {
            // Log the exception fully to understand the error, especially SQLiteConstraintException
            Log.e(TAG, "FATAL: Error during database restore transaction.", e);
            return false;
        }
    }







    public enum SynchronizationState {
        IDLE, CHECKING, PROCESSING, SUCCESS, ERROR, NO_CHANGES
    }

    public FinixRepository(Context context) {
        Log.i(TAG, "FinixRepository initializing...");

        this.context = context; // <--- FIX: Store the context here
        this.contentResolver = context.getContentResolver(); // NEW: Initialize ContentResolver

        FinixDatabase db = FinixDatabase.getDatabase(context);


        categoryDAO = db.categoryDao();
        transactionDAO = db.transactionDao(); // NEW
        savingsGoalDAO = db.savingsGoalDao(); // NEW

        budgetDAO = db.budgetDao(); // NEW
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
        transactionService = retrofit.create(TransactionService.class); // NEW
        budgetService = retrofit.create(BudgetService.class); // NEW
        savingsGoalService = retrofit.create(SavingsGoalService.class); // NEW
        synchronizationLogService = retrofit.create(SynchronizationLogService.class); // NEW LOG SERVICE

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










    /**
     * Public entry point to start the full synchronization process.
     * This will execute categories, followed by transactions, and finally budgets, sequentially.
     */
    public void synchronizeAllData() {
        Log.i(TAG, "--- Starting synchronizeAllData() - Categories first ---");
        synchronizeCategories();
    }







    /**
     * Synchronize all categories logs.
     * This method is the first step in the chain. It will call synchronizeTransactions() upon success.
     */
    public void synchronizeCategories() {
        syncStatusLive.postValue(SynchronizationState.CHECKING);
        Log.i(TAG, "--- Starting synchronizeCategories() ---");

        executorService.execute(() -> {
            try {
                // Ensure DAO access is on the background thread
                List<SynchronizationLog> logsToSync = syncLogDAO.getAllLogs();

                if (logsToSync.isEmpty()) {
                    // If no logs, proceed to check transactions
                    Log.i(TAG, "No category changes to sync. Proceeding to transactions...");
                    synchronizeTransactions(); // <-- CHAIN NEXT STEP
                    return;
                }

                // ✅ Sort logs by local_id of their category
                // NOTE: This sorting logic is highly inefficient and still processes ALL logs,
                // but we keep it here to maintain the original logic while fixing the concurrency.
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

                // 🔥 CRITICAL CHANGE: Instead of posting SUCCESS for the whole process,
                // we chain the next step.
                Log.i(TAG, "Category sync completed successfully. Starting transaction sync...");
                synchronizeTransactions(); // <-- CHAINED CALL

            } catch (Exception e) {
                Log.e(TAG, "FATAL: Database access error during category sync.", e);
                syncStatusLive.postValue(SynchronizationState.ERROR);
            }
        });
    }

    /**
     * Handles all categories synchronization logic
     */
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

    /**
     * Add a category to the server (PENDING status).
     */
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














    /**
     * Synchronize all transaction logs
     * This step now chains to synchronizeBudgets() upon successful completion.
     */
    public void synchronizeTransactions() {
        // We already posted SynchronizationState.CHECKING in synchronizeCategories(), so we don't repeat it.
        Log.i(TAG, "--- Starting synchronizeTransactions() ---");

        executorService.execute(() -> {
            try {
                List<SynchronizationLog> logsToSync = syncLogDAO.getAllLogs();

                // Filter only transaction logs for processing
                List<SynchronizationLog> transactionLogs = new java.util.ArrayList<>();
                List<SynchronizationLog> categoryLogs = new java.util.ArrayList<>();
                // 🆕 Filter out budget logs as well
                List<SynchronizationLog> budgetLogs = new java.util.ArrayList<>();

                for (SynchronizationLog log : logsToSync) {
                    if ("transactions".equals(log.getTableName())) {
                        transactionLogs.add(log);
                    } else if ("categories".equals(log.getTableName())) {
                        categoryLogs.add(log);
                    } else if ("budgets".equals(log.getTableName())) { // 🆕
                        budgetLogs.add(log);
                    }
                }

                if (transactionLogs.isEmpty()) {
                    // If no transaction logs, proceed to check budgets
                    Log.i(TAG, "No pending transaction logs. Proceeding to budgets...");
                    synchronizeBudgets(); // <-- CHAIN NEXT STEP
                    return;
                }

                // ✅ Sort only transaction logs by local_id
                transactionLogs.sort((l1, l2) -> {
                    Transaction t1 = transactionDAO.getTransactionById(l1.getRecordId());
                    Transaction t2 = transactionDAO.getTransactionById(l2.getRecordId());
                    int id1 = t1 != null ? t1.getLocalId() : Integer.MAX_VALUE;
                    int id2 = t2 != null ? t2.getLocalId() : Integer.MAX_VALUE;
                    return Integer.compare(id1, id2);
                });

                syncStatusLive.postValue(SynchronizationState.PROCESSING);

                for (SynchronizationLog log : transactionLogs) {
                    boolean result = handleTransactionSync(log);
                    if (!result) {
                        syncStatusLive.postValue(SynchronizationState.ERROR);
                        return;
                    }
                }

                // 🆕 CRITICAL CHANGE: Chain to the next step (Budgets)
                Log.i(TAG, "Transaction sync completed successfully. Starting budget sync...");
                synchronizeBudgets();

            } catch (Exception e) {
                Log.e(TAG, "FATAL: Error during transaction sync.", e);
                syncStatusLive.postValue(SynchronizationState.ERROR);
            }
        });
    }

    /**
     * Handles all transaction synchronization logic
     */
    private boolean handleTransactionSync(SynchronizationLog log) {
        try {
            int localRecordId = log.getRecordId();
            Transaction transaction = transactionDAO.getTransactionById(localRecordId);

            String transactionInfo = transaction != null
                    ? "Transaction (Amount: " + transaction.getAmount() + ", Type: " + transaction.getType() + ")"
                    : "Unknown Transaction (ID: " + localRecordId + ")";

            switch (log.getStatus()) {
                case "PENDING":
                    if (transaction != null)
                        return addTransactionToServer(transaction, log, transactionInfo);

                    // Transaction deleted locally before sync
                    log.setStatus("SYNCED - STALE");
                    log.setMessage("Transaction not found locally (ID: " + localRecordId + "). Log cleared.");
                    syncLogDAO.update(log);
                    return true;

                case "UPDATED":
                    if (transaction != null && transaction.getId() != 0)
                        return updateTransactionOnServer(transaction, log, transactionInfo);

                    // Update skipped due to missing server ID
                    log.setStatus("SYNCED - STALE");
                    log.setMessage("Update skipped: Transaction missing server ID (local ID: " + localRecordId + ").");
                    syncLogDAO.update(log);
                    return true;

                case "DELETED":
                    return deleteTransactionFromServer(log, transactionInfo);

                default:
                    log.setStatus("SYNCED - STALE");
                    log.setMessage("Unknown status '" + log.getStatus() + "'. Log cleared.");
                    syncLogDAO.update(log);
                    return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in handleTransactionSync for log ID: " + log.getId(), e);
            log.setStatus("ERROR");
            log.setMessage("Internal error processing sync for transaction: " + e.getMessage());
            syncLogDAO.update(log);
            return false;
        }
    }

    /**
     * Add a transaction to the server (PENDING status).
     */
    private boolean addTransactionToServer(Transaction localTransaction, SynchronizationLog log, String transactionInfo) {
        Log.i(TAG, "-> addTransactionToServer. Local JSON: " + gson.toJson(localTransaction));

        try {
            Call<ResponseBody> call = transactionService.createTransaction(localTransaction);
            Response<ResponseBody> response = call.execute();

            if (!response.isSuccessful()) {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                Log.e(TAG, "Server error adding transaction. Code: " + response.code() + " | Error: " + errorBody);

                log.setStatus("ERROR - SERVER");
                log.setMessage("ADD FAILED for " + transactionInfo + ". Code: " + response.code());
                syncLogDAO.update(log);
                return false;
            }

            String rawJsonBody = response.body() != null ? response.body().string() : "";
            Log.i(TAG, "SUCCESSFUL RESPONSE BODY (RAW): " + rawJsonBody);

            TransactionResponse transactionResponse = gson.fromJson(rawJsonBody, TransactionResponse.class);

            if (transactionResponse == null || transactionResponse.data == null) {
                log.setStatus("ERROR - DATA");
                log.setMessage("ADD FAILED for " + transactionInfo + ". Invalid JSON/data.");
                syncLogDAO.update(log);
                return false;
            }

            Transaction serverTransaction = transactionResponse.data;

            // ⚡ Update local transaction with server ID only
            localTransaction.setId(serverTransaction.getId());
            transactionDAO.update(localTransaction);

            log.setStatus("SYNCED - ADDED");
            log.setMessage(transactionInfo + " added successfully. Server ID: " + serverTransaction.getId());
            log.setRecordId(serverTransaction.getId());
            syncLogDAO.update(log);

            Log.i(TAG, "Transaction synced. Server ID: " + serverTransaction.getId() +
                    ", local_id remains: " + localTransaction.getLocalId());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Network error adding transaction.", e);
            log.setStatus("ERROR - NETWORK");
            log.setMessage("ADD FAILED for " + transactionInfo + ". Network: " + e.getMessage());
            syncLogDAO.update(log);
            return false;
        }
    }

    /**
     * Updates an existing transaction on the server (UPDATED status).
     */
    private boolean updateTransactionOnServer(Transaction localTransaction, SynchronizationLog log, String transactionInfo) {
        Log.i(TAG, "-> updateTransactionOnServer. Server ID: " + localTransaction.getId() +
                " | JSON: " + gson.toJson(localTransaction));

        try {
            Call<ResponseBody> call = transactionService.updateTransaction(localTransaction.getId(), localTransaction);
            Response<ResponseBody> response = call.execute();

            String rawJsonBody = response.body() != null ? response.body().string() : "";
            if (!rawJsonBody.isEmpty()) Log.i(TAG, "UPDATE RESPONSE BODY (RAW): " + rawJsonBody);

            if (response.isSuccessful()) {
                log.setStatus("SYNCED - UPDATED");
                log.setMessage(transactionInfo + " updated successfully.");
                syncLogDAO.update(log);
                return true;
            } else {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                Log.e(TAG, "Server error updating transaction. Code: " + response.code() + " | Error: " + errorBody);

                log.setStatus("ERROR - SERVER");
                log.setMessage("UPDATE FAILED for " + transactionInfo + ". Code: " + response.code());
                syncLogDAO.update(log);
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Network error updating transaction.", e);
            log.setStatus("ERROR - NETWORK");
            log.setMessage("UPDATE FAILED for " + transactionInfo + ". Network: " + e.getMessage());
            syncLogDAO.update(log);
            return false;
        }
    }

    /**
     * Deletes a transaction from the server (DELETED status).
     */
    private boolean deleteTransactionFromServer(SynchronizationLog log, String transactionInfo) {
        int serverRecordId = log.getRecordId();
        Log.i(TAG, "-> deleteTransactionFromServer. Deleting record ID: " + serverRecordId);

        try {
            Call<ResponseBody> call = transactionService.deleteTransaction(serverRecordId);
            Response<ResponseBody> response = call.execute();

            String rawJsonBody = response.body() != null ? response.body().string() : "";
            if (!rawJsonBody.isEmpty()) Log.i(TAG, "DELETE RESPONSE BODY (RAW): " + rawJsonBody);

            if (response.isSuccessful() || response.code() == 404) {
                log.setStatus("SYNCED - DELETED");
                log.setMessage("Transaction deleted successfully (or already gone). ID: " + serverRecordId);
                syncLogDAO.update(log);
                Log.i(TAG, "Transaction deleted successfully.");
                return true;
            } else {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                log.setStatus("ERROR - SERVER");
                log.setMessage("DELETE FAILED for Transaction ID " + serverRecordId + ". Code: " + response.code() + ". " + errorBody);
                syncLogDAO.update(log);
                return false;
            }
        } catch (Exception e) {
            log.setStatus("ERROR - NETWORK");
            log.setMessage("DELETE FAILED for Transaction ID " + serverRecordId + ". Network error: " + e.getMessage());
            syncLogDAO.update(log);
            Log.e(TAG, "Network error deleting transaction", e);
            return false;
        }
    }


















    /**
     * Synchronize all budget logs.
     * This is the final step in the chained sync process before posting success/no_changes.
     */
    public void synchronizeBudgets() {
        Log.i(TAG, "--- Starting synchronizeBudgets() ---");

        executorService.execute(() -> {
            try {
                List<SynchronizationLog> logsToSync = syncLogDAO.getAllLogs();

                // Filter only budget logs for processing
                List<SynchronizationLog> budgetLogs = new java.util.ArrayList<>();
                for (SynchronizationLog log : logsToSync) {
                    if ("budgets".equals(log.getTableName())) {
                        budgetLogs.add(log);
                    }
                }

                if (budgetLogs.isEmpty()) {
                    // ❌ REMOVE the final status posts from here
                    Log.i(TAG, "No pending budget logs. Proceeding to Savings Goals..."); // Updated log message
                    synchronizeSavingsGoals(); // <-- CHAIN NEXT STEP
                    return;
                }

                // ✅ Sort budget logs by local_id
                budgetLogs.sort((l1, l2) -> {
                    Budget b1 = budgetDAO.getBudgetById(l1.getRecordId());
                    Budget b2 = budgetDAO.getBudgetById(l2.getRecordId());
                    int id1 = b1 != null ? b1.getLocalId() : Integer.MAX_VALUE;
                    int id2 = b2 != null ? b2.getLocalId() : Integer.MAX_VALUE;
                    return Integer.compare(id1, id2);
                });

                syncStatusLive.postValue(SynchronizationState.PROCESSING);

                for (SynchronizationLog log : budgetLogs) {
                    boolean result = handleBudgetSync(log);
                    if (!result) {
                        syncStatusLive.postValue(SynchronizationState.ERROR);
                        return;
                    }
                }

                // ❌ REMOVE the final SUCCESS post from here
                Log.i(TAG, "Budget sync completed successfully. Starting Savings Goals sync..."); // New log message
                synchronizeSavingsGoals(); // <-- NEW CHAIN STEP


            } catch (Exception e) {
                Log.e(TAG, "FATAL: Error during budget sync.", e);
                syncStatusLive.postValue(SynchronizationState.ERROR);
            }
        });
    }

    /**
     * Handles all budget synchronization logic
     */
    private boolean handleBudgetSync(SynchronizationLog log) {
        try {
            int localRecordId = log.getRecordId();
            Budget budget = budgetDAO.getBudgetById(localRecordId);

            String budgetInfo = budget != null
                    ? "Budget (Amount: " + budget.getBudgetedAmount() + ", Cat ID: " + budget.getCategoryId() + ")"
                    : "Unknown Budget (ID: " + localRecordId + ")";

            switch (log.getStatus()) {
                case "PENDING":
                    if (budget != null)
                        return addBudgetToServer(budget, log, budgetInfo);

                    // Budget deleted locally before sync
                    log.setStatus("SYNCED - STALE");
                    log.setMessage("Budget not found locally (ID: " + localRecordId + "). Log cleared.");
                    syncLogDAO.update(log);
                    return true;

                case "UPDATED":
                    if (budget != null && budget.getId() != 0)
                        return updateBudgetOnServer(budget, log, budgetInfo);

                    // Update skipped due to missing server ID
                    log.setStatus("SYNCED - STALE");
                    log.setMessage("Update skipped: Budget missing server ID (local ID: " + localRecordId + ").");
                    syncLogDAO.update(log);
                    return true;

                case "DELETED":
                    return deleteBudgetFromServer(log, budgetInfo);

                default:
                    log.setStatus("SYNCED - STALE");
                    log.setMessage("Unknown status '" + log.getStatus() + "'. Log cleared.");
                    syncLogDAO.update(log);
                    return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in handleBudgetSync for log ID: " + log.getId(), e);
            log.setStatus("ERROR");
            log.setMessage("Internal error processing sync for budget: " + e.getMessage());
            syncLogDAO.update(log);
            return false;
        }
    }

    /**
     * Add a budget to the server (PENDING status).
     */
    private boolean addBudgetToServer(Budget localBudget, SynchronizationLog log, String budgetInfo) {
        Log.i(TAG, "-> addBudgetToServer. Local JSON: " + gson.toJson(localBudget));

        try {
            Call<ResponseBody> call = budgetService.createBudget(localBudget);
            Response<ResponseBody> response = call.execute();

            if (!response.isSuccessful()) {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                Log.e(TAG, "Server error adding budget. Code: " + response.code() + " | Error: " + errorBody);

                log.setStatus("ERROR - SERVER");
                log.setMessage("ADD FAILED for " + budgetInfo + ". Code: " + response.code());
                syncLogDAO.update(log);
                return false;
            }

            String rawJsonBody = response.body() != null ? response.body().string() : "";
            Log.i(TAG, "SUCCESSFUL RESPONSE BODY (RAW): " + rawJsonBody);

            BudgetResponse budgetResponse = gson.fromJson(rawJsonBody, BudgetResponse.class);

            if (budgetResponse == null || budgetResponse.data == null) {
                log.setStatus("ERROR - DATA");
                log.setMessage("ADD FAILED for " + budgetInfo + ". Invalid JSON/data.");
                syncLogDAO.update(log);
                return false;
            }

            Budget serverBudget = budgetResponse.data;

            // ⚡ Update local budget with server ID only
            localBudget.setId(serverBudget.getId());
            budgetDAO.update(localBudget);

            log.setStatus("SYNCED - ADDED");
            log.setMessage(budgetInfo + " added successfully. Server ID: " + serverBudget.getId());
            log.setRecordId(serverBudget.getId());
            syncLogDAO.update(log);

            Log.i(TAG, "Budget synced. Server ID: " + serverBudget.getId() +
                    ", local_id remains: " + localBudget.getLocalId());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Network error adding budget.", e);
            log.setStatus("ERROR - NETWORK");
            log.setMessage("ADD FAILED for " + budgetInfo + ". Network: " + e.getMessage());
            syncLogDAO.update(log);
            return false;
        }
    }

    /**
     * Updates an existing budget on the server (UPDATED status).
     */
    private boolean updateBudgetOnServer(Budget localBudget, SynchronizationLog log, String budgetInfo) {
        Log.i(TAG, "-> updateBudgetOnServer. Server ID: " + localBudget.getId() +
                " | JSON: " + gson.toJson(localBudget));

        try {
            Call<ResponseBody> call = budgetService.updateBudget(localBudget.getId(), localBudget);
            Response<ResponseBody> response = call.execute();

            String rawJsonBody = response.body() != null ? response.body().string() : "";
            if (!rawJsonBody.isEmpty()) Log.i(TAG, "UPDATE RESPONSE BODY (RAW): " + rawJsonBody);

            if (response.isSuccessful()) {
                log.setStatus("SYNCED - UPDATED");
                log.setMessage(budgetInfo + " updated successfully.");
                syncLogDAO.update(log);
                return true;
            } else {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                Log.e(TAG, "Server error updating budget. Code: " + response.code() + " | Error: " + errorBody);

                log.setStatus("ERROR - SERVER");
                log.setMessage("UPDATE FAILED for " + budgetInfo + ". Code: " + response.code());
                syncLogDAO.update(log);
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Network error updating budget.", e);
            log.setStatus("ERROR - NETWORK");
            log.setMessage("UPDATE FAILED for " + budgetInfo + ". Network: " + e.getMessage());
            syncLogDAO.update(log);
            return false;
        }
    }

    /**
     * Deletes a budget from the server (DELETED status).
     */
    private boolean deleteBudgetFromServer(SynchronizationLog log, String budgetInfo) {
        int serverRecordId = log.getRecordId();
        Log.i(TAG, "-> deleteBudgetFromServer. Deleting record ID: " + serverRecordId);

        try {
            Call<ResponseBody> call = budgetService.deleteBudget(serverRecordId);
            Response<ResponseBody> response = call.execute();

            String rawJsonBody = response.body() != null ? response.body().string() : "";
            if (!rawJsonBody.isEmpty()) Log.i(TAG, "DELETE RESPONSE BODY (RAW): " + rawJsonBody);

            if (response.isSuccessful() || response.code() == 404) {
                log.setStatus("SYNCED - DELETED");
                log.setMessage("Budget deleted successfully (or already gone). ID: " + serverRecordId);
                syncLogDAO.update(log);
                Log.i(TAG, "Budget deleted successfully.");
                return true;
            } else {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                log.setStatus("ERROR - SERVER");
                log.setMessage("DELETE FAILED for Budget ID " + serverRecordId + ". Code: " + response.code() + ". " + errorBody);
                syncLogDAO.update(log);
                return false;
            }
        } catch (Exception e) {
            log.setStatus("ERROR - NETWORK");
            log.setMessage("DELETE FAILED for Budget ID " + serverRecordId + ". Network error: " + e.getMessage());
            syncLogDAO.update(log);
            Log.e(TAG, "Network error deleting budget", e);
            return false;
        }
    }



















    /**
     * Synchronize all savings goal logs.
     * This is the final step in the chained sync process before posting final status.
     */
    public void synchronizeSavingsGoals() {
        Log.i(TAG, "--- Starting synchronizeSavingsGoals() ---");

        executorService.execute(() -> {
            try {
                List<SynchronizationLog> logsToSync = syncLogDAO.getAllLogs();

                // Filter only savings_goals logs for processing
                List<SynchronizationLog> goalLogs = new java.util.ArrayList<>();
                for (SynchronizationLog log : logsToSync) {
                    if ("savings_goals".equals(log.getTableName())) {
                        goalLogs.add(log);
                    }
                }

                if (goalLogs.isEmpty()) {
                    Log.i(TAG, "No pending savings goal logs. Proceeding to Synchronization Logs..."); // Updated log message
                    synchronizeSyncLogs(); // <-- CHAIN NEXT STEP
                    return;
                }

                // ✅ Sort savings goal logs by local_id (similar to other entities)
                goalLogs.sort((l1, l2) -> {
                    SavingsGoal g1 = savingsGoalDAO.getSavingsGoalById(l1.getRecordId());
                    SavingsGoal g2 = savingsGoalDAO.getSavingsGoalById(l2.getRecordId());
                    int id1 = g1 != null ? g1.getLocalId() : Integer.MAX_VALUE;
                    int id2 = g2 != null ? g2.getLocalId() : Integer.MAX_VALUE;
                    return Integer.compare(id1, id2);
                });

                syncStatusLive.postValue(SynchronizationState.PROCESSING);

                for (SynchronizationLog log : goalLogs) {
                    boolean result = handleSavingsGoalSync(log);
                    if (!result) {
                        syncStatusLive.postValue(SynchronizationState.ERROR); // Post final status
                        return;
                    }
                }

                Log.i(TAG, "Savings Goal sync completed successfully. Starting Synchronization Log sync...");
                synchronizeSyncLogs(); // <-- CHAIN NEXT STEP

            } catch (Exception e) {
                Log.e(TAG, "FATAL: Error during savings goal sync.", e);
                syncStatusLive.postValue(SynchronizationState.ERROR);
            }
        });
    }

    /**
     * Handles all savings goal synchronization logic
     */
    private boolean handleSavingsGoalSync(SynchronizationLog log) {
        try {
            int localRecordId = log.getRecordId();
            SavingsGoal goal = savingsGoalDAO.getSavingsGoalById(localRecordId);

            String goalInfo = goal != null
                    ? "SavingsGoal (Name: " + goal.getGoalName() + ", Amount: " + goal.getTargetAmount() + ")"
                    : "Unknown SavingsGoal (ID: " + localRecordId + ")";

            switch (log.getStatus()) {
                case "PENDING":
                    if (goal != null)
                        return addSavingsGoalToServer(goal, log, goalInfo);

                    // Goal deleted locally before sync
                    log.setStatus("SYNCED - STALE");
                    log.setMessage("Savings Goal not found locally (ID: " + localRecordId + "). Log cleared.");
                    syncLogDAO.update(log);
                    return true;

                case "UPDATED":
                    if (goal != null && goal.getId() != 0)
                        return updateSavingsGoalOnServer(goal, log, goalInfo);

                    // Update skipped due to missing server ID
                    log.setStatus("SYNCED - STALE");
                    log.setMessage("Update skipped: Savings Goal missing server ID (local ID: " + localRecordId + ").");
                    syncLogDAO.update(log);
                    return true;

                case "DELETED":
                    // For DELETED, we use the ID stored in the log (which should be the Server ID)
                    return deleteSavingsGoalFromServer(log, goalInfo);

                default:
                    log.setStatus("SYNCED - STALE");
                    log.setMessage("Unknown status '" + log.getStatus() + "'. Log cleared.");
                    syncLogDAO.update(log);
                    return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in handleSavingsGoalSync for log ID: " + log.getId(), e);
            log.setStatus("ERROR");
            log.setMessage("Internal error processing sync for savings goal: " + e.getMessage());
            syncLogDAO.update(log);
            return false;
        }
    }

    /**
     * Add a savings goal to the server (PENDING status).
     */
    private boolean addSavingsGoalToServer(SavingsGoal localGoal, SynchronizationLog log, String goalInfo) {
        Log.i(TAG, "-> addSavingsGoalToServer. Local JSON: " + gson.toJson(localGoal));

        try {
            Call<ResponseBody> call = savingsGoalService.createSavingsGoal(localGoal);
            Response<ResponseBody> response = call.execute();

            if (!response.isSuccessful()) {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                Log.e(TAG, "Server error adding savings goal. Code: " + response.code() + " | Error: " + errorBody);

                log.setStatus("ERROR - SERVER");
                log.setMessage("ADD FAILED for " + goalInfo + ". Code: " + response.code());
                syncLogDAO.update(log);
                return false;
            }

            // 🟢 FIX: Use try-with-resources to ensure the ResponseBody is closed
            try (ResponseBody body = response.body()) {
                if (body == null) {
                    log.setStatus("ERROR - DATA");
                    log.setMessage("ADD FAILED for " + goalInfo + ". Empty response body.");
                    syncLogDAO.update(log);
                    return false;
                }

                String rawJsonBody = body.string();
                Log.i(TAG, "SUCCESSFUL RESPONSE BODY (RAW): " + rawJsonBody);

                SavingsGoalResponse goalResponse = gson.fromJson(rawJsonBody, SavingsGoalResponse.class);

                if (goalResponse == null || goalResponse.data == null) {
                    log.setStatus("ERROR - DATA");
                    log.setMessage("ADD FAILED for " + goalInfo + ". Invalid JSON/data.");
                    syncLogDAO.update(log);
                    return false;
                }

                SavingsGoal serverGoal = goalResponse.data;

                // 1. Update local goal object with the server ID
                localGoal.setId(serverGoal.getId());

                // 2. CRITICAL PERSISTENCE STEP: Commit the change to the local Room database
                // This line now runs after the network stream is guaranteed to be closed.
                savingsGoalDAO.update(localGoal);

                // 3. Update the sync log
                log.setStatus("SYNCED - ADDED");
                log.setMessage(goalInfo + " added successfully. Server ID: " + serverGoal.getId());
                log.setRecordId(serverGoal.getId());
                syncLogDAO.update(log);

                Log.i(TAG, "Savings Goal synced. Server ID: " + serverGoal.getId() +
                        ", local_id remains: " + localGoal.getLocalId());
                return true;
            }

        } catch (Exception e) {
            Log.e(TAG, "Network error adding savings goal or persistence failed.", e);
            log.setStatus("ERROR - NETWORK");
            log.setMessage("ADD FAILED for " + goalInfo + ". Network/Persistence error: " + e.getMessage());
            syncLogDAO.update(log);
            return false;
        }
    }

    /**
     * Updates an existing savings goal on the server (UPDATED status).
     */
    private boolean updateSavingsGoalOnServer(SavingsGoal localGoal, SynchronizationLog log, String goalInfo) {
        Log.i(TAG, "-> updateSavingsGoalOnServer. Server ID: " + localGoal.getId() +
                " | JSON: " + gson.toJson(localGoal));

        try {
            Call<ResponseBody> call = savingsGoalService.updateSavingsGoal(localGoal.getId(), localGoal);
            Response<ResponseBody> response = call.execute();

            String rawJsonBody = response.body() != null ? response.body().string() : "";
            if (!rawJsonBody.isEmpty()) Log.i(TAG, "UPDATE RESPONSE BODY (RAW): " + rawJsonBody);

            if (response.isSuccessful()) {
                log.setStatus("SYNCED - UPDATED");
                log.setMessage(goalInfo + " updated successfully.");
                syncLogDAO.update(log);
                return true;
            } else {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                Log.e(TAG, "Server error updating savings goal. Code: " + response.code() + " | Error: " + errorBody);

                log.setStatus("ERROR - SERVER");
                log.setMessage("UPDATE FAILED for " + goalInfo + ". Code: " + response.code());
                syncLogDAO.update(log);
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Network error updating savings goal.", e);
            log.setStatus("ERROR - NETWORK");
            log.setMessage("UPDATE FAILED for " + goalInfo + ". Network: " + e.getMessage());
            syncLogDAO.update(log);
            return false;
        }
    }

    /**
     * Deletes a savings goal from the server (DELETED status).
     */
    private boolean deleteSavingsGoalFromServer(SynchronizationLog log, String goalInfo) {
        int serverRecordId = log.getRecordId();
        Log.i(TAG, "-> deleteSavingsGoalFromServer. Deleting record ID: " + serverRecordId);

        try {
            Call<ResponseBody> call = savingsGoalService.deleteSavingsGoal(serverRecordId);
            Response<ResponseBody> response = call.execute();

            String rawJsonBody = response.body() != null ? response.body().string() : "";
            if (!rawJsonBody.isEmpty()) Log.i(TAG, "DELETE RESPONSE BODY (RAW): " + rawJsonBody);

            if (response.isSuccessful() || response.code() == 404) {
                log.setStatus("SYNCED - DELETED");
                log.setMessage("Savings Goal deleted successfully (or already gone). ID: " + serverRecordId);
                syncLogDAO.update(log);
                Log.i(TAG, "Savings Goal deleted successfully.");
                return true;
            } else {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                log.setStatus("ERROR - SERVER");
                log.setMessage("DELETE FAILED for Savings Goal ID " + serverRecordId + ". Code: " + response.code() + ". " + errorBody);
                syncLogDAO.update(log);
                return false;
            }
        } catch (Exception e) {
            log.setStatus("ERROR - NETWORK");
            log.setMessage("DELETE FAILED for Savings Goal ID " + serverRecordId + ". Network error: " + e.getMessage());
            syncLogDAO.update(log);
            Log.e(TAG, "Network error deleting savings goal", e);
            return false;
        }
    }



















    public void synchronizeSyncLogs() {
        Log.i(TAG, "--- Starting synchronizeSyncLogs() (FULL MIRROR MODE) ---");

        executorService.execute(() -> {
            try {
                syncStatusLive.postValue(SynchronizationState.PROCESSING);

                // 1. Fetch all local logs
                List<SynchronizationLog> localLogs = syncLogDAO.getAllLogs();
                if (localLogs.isEmpty()) {
                    Log.i(TAG, "Local sync log is empty. Synchronization complete.");
                    syncStatusLive.postValue(SynchronizationState.NO_CHANGES);
                    return;
                }

                // Print all logs to logcat for debugging (kept for visibility)
                Log.d(TAG, "Local Logs found (" + localLogs.size() + "):");
                for (SynchronizationLog log : localLogs) {
                    // Assuming Log model has getters:
                    Log.d(TAG, "  Log ID: " + log.getId() + ", Table: " + log.getTableName() +
                            ", Record ID: " + log.getRecordId() + ", Status: " + log.getStatus());
                }

                // 2. CRITICAL STEP: DELETE ALL EXISTING LOGS ON THE SERVER
                // NOTE: This assumes your Retrofit service (synchronizationLogService) has a method:
                // Call<ResponseBody> deleteAllLogs();
                Log.w(TAG, "Executing full server wipe of synchronization logs...");
                Response<ResponseBody> deleteResponse = synchronizationLogService.deleteAllLogs().execute();

                if (!deleteResponse.isSuccessful()) {
                    String errorBody = deleteResponse.errorBody() != null ? deleteResponse.errorBody().string() : "No error body";
                    Log.e(TAG, "FATAL: Failed to wipe server sync logs. Aborting sync. Code: " + deleteResponse.code() + ". Error: " + errorBody);
                    syncStatusLive.postValue(SynchronizationState.ERROR);
                    return;
                }
                Log.i(TAG, "Server sync logs successfully wiped. Proceeding to re-POST all local logs.");

                int logsSynced = 0;

                // 3. Iterate over ALL local logs and POST them to the clean server
                for (SynchronizationLog localLog : localLogs) {
                    // We always treat it as a new record on the server now
                    int newServerId = postLogEntryToServer(localLog);

                    if (newServerId != -1) {
                        logsSynced++;
                        // The postLogEntryToServer method already handles updating the local DAO status.
                    } else {
                        Log.e(TAG, "Failed to re-POST log for table " + localLog.getTableName() + ". This log entry was not mirrored to the server.");
                        // Non-critical fail, continue loop, but log the error
                    }
                }

                Log.i(TAG, "Synchronization Log sync completed. Logs processed: " + localLogs.size() + ", Logs synced: " + logsSynced + ", Logs failed: " + (localLogs.size() - logsSynced));
                syncStatusLive.postValue(SynchronizationState.SUCCESS);

            } catch (Exception e) {
                Log.e(TAG, "FATAL: Error during synchronization log sync.", e);
                syncStatusLive.postValue(SynchronizationState.ERROR);
            }
        });
    }

    /**
     * Handles the network operation to POST a single SynchronizationLog entry to the server.
     * It is assumed the server logs are empty when this is called (Full Mirror Mode).
     * @param log The local log entry to be sent.
     * @return The new server ID (int) if a POST is successful, or -1 if the operation fails.
     */
    private int postLogEntryToServer(SynchronizationLog log) {
        String logInfo = log.getTableName() + " (Record ID: " + log.getRecordId() + ")";

        Log.i(TAG, "-> POST Synchronization Log for " + logInfo);

        try {
            Call<ResponseBody> call = synchronizationLogService.createLog(log);
            Response<ResponseBody> response = call.execute();

            if (response.isSuccessful()) {
                Log.i(TAG, "Synchronization Log POST successful for " + logInfo);

                String jsonBody = response.body() != null ? response.body().string() : null;
                int newServerId = -1;

                if (jsonBody != null) {
                    // Manually parse the JSON to get the server-assigned ID
                    Gson gson = new Gson();
                    // Assuming SynchronizationLogResponse is a class accessible here
                    // Note: The structure must match your ORDS response for a successful POST.
                    SynchronizationLogResponse logResponse = gson.fromJson(jsonBody, SynchronizationLogResponse.class);

                    if (logResponse != null && logResponse.data != null) {
                        newServerId = logResponse.data.getId();
                    }
                }

                if (newServerId != -1) {
                    // Update local status with the newly assigned server ID
                    syncLogDAO.updateLogStatusToSynced(log.getId(), newServerId, System.currentTimeMillis());
                    Log.i(TAG, "Local log (ID: " + log.getId() + ") status updated to SYNCED with Server ID: " + newServerId);
                    return newServerId; // Success: Return the new Server ID
                } else {
                    Log.e(TAG, "POST successful, but failed to parse server ID from response for: " + logInfo);
                    return -1; // Fail: Parsing error
                }
            } else {
                String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                Log.e(TAG, "Server error during POST of log " + logInfo + ". Code: " + response.code() + ". Error: " + errorBody);
                return -1; // Fail: Server error
            }
        } catch (Exception e) {
            Log.e(TAG, "Network error during POST of log " + logInfo, e);
            return -1; // Fail: Network/other exception
        }
    }
}