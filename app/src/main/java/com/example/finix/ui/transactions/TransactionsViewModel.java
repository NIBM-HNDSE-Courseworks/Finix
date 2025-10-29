// TransactionsViewModel.java

package com.example.finix.ui.transactions;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;

import com.example.finix.data.*; // Assuming this imports Transaction and Category classes

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class TransactionsViewModel extends AndroidViewModel {

    private final MutableLiveData<List<Transaction>> incomeLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Transaction>> expenseLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Map<Integer, String>> categoryMapLive = new MutableLiveData<>(new HashMap<>());
    // 🆕 NEW: LiveData for the distinct Month/Year strings (e.g., "October 2025")
    private final MutableLiveData<List<String>> distinctMonthsLive = new MutableLiveData<>(new ArrayList<>());

    private final FinixDatabase db;

    private final MutableLiveData<String> _messageEvent = new MutableLiveData<>();
    public LiveData<String> getMessageEvent() { return _messageEvent; }

    public TransactionsViewModel(@NonNull Application app) {
        super(app);
        db = FinixDatabase.getDatabase(app);
        loadCategories(); // Load categories first
        loadAllTransactions();
        fetchLatestCategoryMap(); // So adapters get initial names even before editing
        loadDistinctMonths(); // 🆕 NEW: Load distinct months/years
    }

    public LiveData<List<Transaction>> getIncomeTransactions() { return incomeLive; }
    public LiveData<List<Transaction>> getExpenseTransactions() { return expenseLive; }
    public LiveData<Map<Integer, String>> getCategoriesLive() { return categoryMapLive; }
    // 🆕 NEW: Getter for the distinct months LiveData
    public LiveData<List<String>> getDistinctMonthsLive() { return distinctMonthsLive; }

    public Map<Integer, String> getCategoryMap() { return categoryMapLive.getValue(); }

    public void addCategory(String name) {
        if (name == null || name.trim().isEmpty()) return;

        new Thread(() -> {
            db.categoryDao().insert(new Category(name.trim()));
            loadCategories(); // Reload after insertion
        }).start();
    }

    /**
     * 🆕 MODIFIED: Performs the blocking database read and posts results, now accepting optional date filters.
     * @param monthYearString The month and year string to filter by (e.g., "October 2025"), or null for all.
     */
    private void _doLoadTransactionsAndPost(String monthYearString) {
        List<Transaction> all = db.transactionDao().getAllTransactions();

        // If a filter is applied, perform filtering on the list
        if (monthYearString != null) {
            SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

            all = all.stream()
                    .filter(t -> monthYearString.equals(monthYearFormat.format(new Date(t.getDateTime()))))
                    .collect(Collectors.toList());
        }

        incomeLive.postValue(all.stream()
                .filter(t -> "Income".equals(t.getType()))
                .collect(Collectors.toList()));
        expenseLive.postValue(all.stream()
                .filter(t -> "Expense".equals(t.getType()))
                .collect(Collectors.toList()));
    }

    // 🔄 UPDATED: Public method starts a new thread to call the private helper (no filter).
    public void loadAllTransactions() {
        new Thread(() -> _doLoadTransactionsAndPost(null)).start();
    }

    /**
     * 🆕 NEW: Filters transactions by month and year.
     * @param monthYearString The month and year string (e.g., "October 2025").
     */
    public void filterByMonthYear(String monthYearString) {
        // Run the load/filter on a background thread
        new Thread(() -> {
            _doLoadTransactionsAndPost(monthYearString);
        }).start();
    }

    /**
     * 🆕 NEW: Loads all distinct Month/Year strings where transactions exist.
     */
    public void loadDistinctMonths() {
        new Thread(() -> {
            List<Long> distinctTimeMillis = db.transactionDao().getDistinctMonthYear();
            Set<String> distinctMonthYearSet = new LinkedHashSet<>();
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

            for (Long time : distinctTimeMillis) {
                // Format the long timestamp into a readable Month Year string
                distinctMonthYearSet.add(sdf.format(new Date(time)));
            }

            // Convert the Set to a List and post the value
            distinctMonthsLive.postValue(new ArrayList<>(distinctMonthYearSet));
        }).start();
    }

    public void loadCategories() {
        new Thread(() -> {
            Map<Integer, String> map = new HashMap<>();
            for (Category c : db.categoryDao().getAllCategories()) {
                map.put(c.getId(), c.getName());
            }
            categoryMapLive.postValue(map);
        }).start();
    }

    public void fetchLatestCategoryMap() {
        new Thread(() -> {
            Map<Integer, String> map = new HashMap<>();
            List<Category> allCategories = db.categoryDao().getAllCategories();

            if (allCategories != null) {
                for (Category c : allCategories) {
                    map.put(c.getId(), c.getName());
                }
                categoryMapLive.postValue(map);
            }
        }).start();
    }

    public void saveTransaction(double amount, String type, int categoryId, long dateTime, String description, Runnable onComplete) {
        new Thread(() -> {
            // 1. Save to DB
            db.transactionDao().insert(new Transaction(amount, type, categoryId, dateTime, description));

            // 2. ⚡ FIX: Call the synchronous helper on this same thread (no filter applied after save).
            // NOTE: This call updates incomeLive/expenseLive to show the LATEST data (all transactions).
            _doLoadTransactionsAndPost(null);

            // 3. 🆕 NEW: Reload distinct months
            loadDistinctMonths();

            // 4. Reload categories just in case
            loadCategories();

            // 5. Execute callback on the main thread after DB operations complete
            // This is the correct place to call the 'onSuccess' or 'onComplete' action.
            if (onComplete != null) new android.os.Handler(getApplication().getMainLooper()).post(onComplete);
        }).start();

        // ❌ REMOVED: The premature call to 'onSuccess.run()' that was here.
    }

    public void updateTransaction(Transaction transaction) {
        new Thread(() -> {
            // 1. Update DB
            db.transactionDao().update(transaction);

            // 2. ⚡ FIX: Call the synchronous helper on this same thread (no filter applied after update).
            _doLoadTransactionsAndPost(null);

            // 3. 🆕 NEW: Reload distinct months
            loadDistinctMonths();

            // 4. Reload categories just in case
            loadCategories();
        }).start();
    }

    public void deleteTransaction(Transaction transaction) {
        new Thread(() -> {
            // 1. Delete from DB
            db.transactionDao().delete(transaction);

            // 2. ⚡ FIX: Call the synchronous helper on this same thread (no filter applied after delete).
            _doLoadTransactionsAndPost(null);

            // 3. 🆕 NEW: Reload distinct months
            loadDistinctMonths();

            // 4. Reload categories just in case
            loadCategories();
        }).start();
    }

    public void sortTransactions(String type, String mode) {
        List<Transaction> current = type.equals("Income")
                ? new ArrayList<>(Objects.requireNonNull(incomeLive.getValue()))
                : new ArrayList<>(Objects.requireNonNull(expenseLive.getValue()));

        switch (mode) {
            case "date_desc":
                current.sort((a, b) -> Long.compare(b.getDateTime(), a.getDateTime()));
                break;
            case "date_asc":
                current.sort(Comparator.comparingLong(Transaction::getDateTime));
                break;
            case "amount_desc":
                current.sort((a, b) -> Double.compare(b.getAmount(), a.getAmount()));
                break;
            case "amount_asc":
                current.sort(Comparator.comparingDouble(Transaction::getAmount));
                break;
        }

        if (type.equals("Income")) incomeLive.setValue(current);
        else expenseLive.setValue(current);
    }

    public void filterByCategory(String type, Integer categoryId, Runnable onComplete, Runnable onNoResults) {
        new Thread(() -> {
            // NOTE: The month/year filter is ignored when filtering by category
            List<Transaction> all = db.transactionDao().getAllTransactions();
            List<Transaction> filtered;

            if (type.equals("Income")) {
                filtered = all.stream()
                        .filter(t -> "Income".equals(t.getType()))
                        .filter(t -> categoryId == null || t.getCategoryId() == categoryId)
                        .collect(Collectors.toList());

                if (categoryId != null && filtered.isEmpty()) {
                    if (onNoResults != null) new android.os.Handler(getApplication().getMainLooper()).post(onNoResults);
                } else {
                    incomeLive.postValue(filtered);
                    if (onComplete != null) new android.os.Handler(getApplication().getMainLooper()).post(onComplete);
                }
            } else { // Expense
                filtered = all.stream()
                        .filter(t -> "Expense".equals(t.getType()))
                        .filter(t -> categoryId == null || t.getCategoryId() == categoryId)
                        .collect(Collectors.toList());

                if (categoryId != null && filtered.isEmpty()) {
                    if (onNoResults != null) new android.os.Handler(getApplication().getMainLooper()).post(onNoResults);
                } else {
                    expenseLive.postValue(filtered);
                    if (onComplete != null) new android.os.Handler(getApplication().getMainLooper()).post(onComplete);
                }
            }
        }).start();
    }
}