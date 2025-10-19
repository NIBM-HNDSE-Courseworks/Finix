// TransactionsViewModel.java

package com.example.finix.ui.transactions;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;

import com.example.finix.data.*; // Assuming this imports Transaction and Category classes

import java.util.*;
import java.util.stream.Collectors;

public class TransactionsViewModel extends AndroidViewModel {

    private final MutableLiveData<List<Transaction>> incomeLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Transaction>> expenseLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Map<Integer, String>> categoryMapLive = new MutableLiveData<>(new HashMap<>());

    private final FinixDatabase db;

    private final MutableLiveData<String> _messageEvent = new MutableLiveData<>();
    public LiveData<String> getMessageEvent() { return _messageEvent; }

    public TransactionsViewModel(@NonNull Application app) {
        super(app);
        db = FinixDatabase.getDatabase(app);
        loadCategories(); // Load categories first
        loadAllTransactions();
        fetchLatestCategoryMap(); // So adapters get initial names even before editing
    }

    public LiveData<List<Transaction>> getIncomeTransactions() { return incomeLive; }
    public LiveData<List<Transaction>> getExpenseTransactions() { return expenseLive; }

    public LiveData<Map<Integer, String>> getCategoriesLive() { return categoryMapLive; }

    public Map<Integer, String> getCategoryMap() { return categoryMapLive.getValue(); }

    public void addCategory(String name) {
        if (name == null || name.trim().isEmpty()) return;

        new Thread(() -> {
            db.categoryDao().insert(new Category(name.trim()));
            loadCategories(); // Reload after insertion
        }).start();
    }

    // ✅ FIX HELPER: Performs the blocking database read and posts results.
    // This runs on the calling thread (must be a background thread).
    private void _doLoadTransactionsAndPost() {
        List<Transaction> all = db.transactionDao().getAllTransactions();
        incomeLive.postValue(all.stream()
                .filter(t -> "Income".equals(t.getType()))
                .collect(Collectors.toList()));
        expenseLive.postValue(all.stream()
                .filter(t -> "Expense".equals(t.getType()))
                .collect(Collectors.toList()));
    }

    // 🔄 UPDATED: Public method starts a new thread to call the private helper.
    public void loadAllTransactions() {
        new Thread(this::_doLoadTransactionsAndPost).start();
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

    // 🛠️ CORRECTED: Fixed the map.put error below.
    public void fetchLatestCategoryMap() {
        new Thread(() -> {
            Map<Integer, String> map = new HashMap<>();
            List<Category> allCategories = db.categoryDao().getAllCategories();

            if (allCategories != null) {
                for (Category c : allCategories) {
                    // Corrected from map.put(c.getId(), c.getId(), c.getName());
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

            // 2. ⚡ FIX: Call the synchronous helper on this same thread.
            _doLoadTransactionsAndPost();

            // 3. Reload categories just in case
            loadCategories();

            // 4. Execute callback on the main thread after DB operations complete
            if (onComplete != null) new android.os.Handler(getApplication().getMainLooper()).post(onComplete);
        }).start();
    }

    public void updateTransaction(Transaction transaction) {
        new Thread(() -> {
            // 1. Update DB
            db.transactionDao().update(transaction);

            // 2. ⚡ FIX: Call the synchronous helper on this same thread.
            _doLoadTransactionsAndPost();

            // 3. Reload categories just in case
            loadCategories();
        }).start();
    }

    public void deleteTransaction(Transaction transaction) {
        new Thread(() -> {
            // 1. Delete from DB
            db.transactionDao().delete(transaction);

            // 2. ⚡ FIX: Call the synchronous helper on this same thread.
            _doLoadTransactionsAndPost();

            // 3. Reload categories just in case
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

    // 🔄 UPDATED: Added onNoResults Runnable to prevent posting empty list to LiveData
    public void filterByCategory(String type, Integer categoryId, Runnable onComplete, Runnable onNoResults) {
        new Thread(() -> {
            List<Transaction> all = db.transactionDao().getAllTransactions();
            List<Transaction> filtered;

            if (type.equals("Income")) {
                filtered = all.stream()
                        .filter(t -> "Income".equals(t.getType()))
                        .filter(t -> categoryId == null || t.getCategoryId() == categoryId)
                        .collect(Collectors.toList());

                if (categoryId != null && filtered.isEmpty()) {
                    // Category filter applied, but NO results.
                    // 1. Run the toast (onNoResults).
                    // 2. DO NOT update LiveData.
                    // 3. DO NOT run onComplete (which updates the filter text).
                    if (onNoResults != null) new android.os.Handler(getApplication().getMainLooper()).post(onNoResults);
                } else {
                    // Update the list (either 'Show All' or a non-empty filter result).
                    incomeLive.postValue(filtered);
                    // 🚨 Move onComplete (update text) HERE, only when the list is actually updated.
                    if (onComplete != null) new android.os.Handler(getApplication().getMainLooper()).post(onComplete);
                }
            } else { // Expense
                filtered = all.stream()
                        .filter(t -> "Expense".equals(t.getType()))
                        .filter(t -> categoryId == null || t.getCategoryId() == categoryId)
                        .collect(Collectors.toList());

                if (categoryId != null && filtered.isEmpty()) {
                    // Category filter applied, but NO results.
                    // 1. Run the toast (onNoResults).
                    // 2. DO NOT update LiveData.
                    // 3. DO NOT run onComplete (which updates the filter text).
                    if (onNoResults != null) new android.os.Handler(getApplication().getMainLooper()).post(onNoResults);
                } else {
                    // Update the list normally.
                    expenseLive.postValue(filtered);
                    // 🚨 Move onComplete (update text) HERE, only when the list is actually updated.
                    if (onComplete != null) new android.os.Handler(getApplication().getMainLooper()).post(onComplete);
                }
            }

            // ❌ REMOVE the unconditional onComplete call from here.
            // if (onComplete != null) new android.os.Handler(getApplication().getMainLooper()).post(onComplete);

        }).start();
    }
}