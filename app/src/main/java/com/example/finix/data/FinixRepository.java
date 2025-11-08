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

public class FinixRepository {

    private static final String TAG = "FinixRepository_LOG";
    private static final String BASE_URL = "http://192.168.8.182:8080/ords/";

    private final CategoryDAO categoryDAO;
    private final TransactionDAO transactionDAO; // NEW
    private final BudgetDAO budgetDAO; // NEW
    private final SynchronizationLogDAO syncLogDAO;
    private final CategoryService categoryService;
    private final TransactionService transactionService; // NEW
    private final BudgetService budgetService; // NEW
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
        transactionDAO = db.transactionDao(); // NEW
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
                    // If no budget logs, check if there are other pending logs.
                    // If only SYNCED logs remain, post NO_CHANGES.
                    if (logsToSync.stream().allMatch(log -> log.getStatus().startsWith("SYNCED"))) {
                        syncStatusLive.postValue(SynchronizationState.NO_CHANGES);
                    } else {
                        syncStatusLive.postValue(SynchronizationState.SUCCESS);
                    }
                    Log.i(TAG, "No pending budget logs. Sync complete (SUCCESS/NO_CHANGES posted).");
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

                // Post overall SUCCESS once budgets are complete
                syncStatusLive.postValue(SynchronizationState.SUCCESS);

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
}