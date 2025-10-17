package com.example.finix.ui.transactions;

import android.app.Application;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.example.finix.data.*;
import java.util.*;
import java.util.stream.Collectors;

public class TransactionsViewModel extends AndroidViewModel {

    private final MutableLiveData<List<Transaction>> incomeLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Transaction>> expenseLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> categoriesLive = new MutableLiveData<>(new ArrayList<>());

    private final FinixDatabase db;

    public TransactionsViewModel(@NonNull Application app) {
        super(app);
        db = FinixDatabase.getDatabase(app);
        loadAllTransactions();
        loadCategories();
    }

    public LiveData<List<Transaction>> getIncomeTransactions() { return incomeLive; }
    public LiveData<List<Transaction>> getExpenseTransactions() { return expenseLive; }
    public LiveData<List<String>> getCategoriesLive() { return categoriesLive; }

    public void addCategory(String name) {
        if (name == null || name.trim().isEmpty()) return;

        new Thread(() -> {
            // Insert new category into DB
            db.categoryDao().insert(new Category(name.trim()));
            // Reload categories LiveData
            loadCategories();
        }).start();
    }

    private void loadAllTransactions() {
        new Thread(() -> {
            List<Transaction> all = db.transactionDao().getAllTransactions();
            incomeLive.postValue(all.stream().filter(t -> "Income".equals(t.getType())).collect(Collectors.toList()));
            expenseLive.postValue(all.stream().filter(t -> "Expense".equals(t.getType())).collect(Collectors.toList()));
        }).start();
    }

    public void loadCategories() {
        new Thread(() -> {
            List<String> list = new ArrayList<>();
            for (Category c : db.categoryDao().getAllCategories()) list.add(c.getName());
            categoriesLive.postValue(list);
        }).start();
    }

    public void saveTransaction(double amount, String type, String category, long dateTime, String description) {
        new Thread(() -> {
            db.transactionDao().insert(new Transaction(amount, type, category, dateTime, description));
            loadAllTransactions();
        }).start();
    }

    public void updateTransaction(Transaction transaction) {
        new Thread(() -> {
            db.transactionDao().update(transaction);
            loadAllTransactions();
        }).start();
    }

    public void deleteTransaction(Transaction transaction) {
        new Thread(() -> {
            db.transactionDao().delete(transaction);
            loadAllTransactions();
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

    public void filterByCategory(String type, String category, Runnable onSuccess, Runnable onNoResults) {
        new Thread(() -> {
            List<Transaction> list = db.transactionDao().getTransactionsByType(type);
            List<Transaction> filtered;

            if (category == null) {
                filtered = list;
            } else {
                filtered = list.stream()
                        .filter(t -> t.getCategory().equalsIgnoreCase(category))
                        .collect(Collectors.toList());

                if (filtered.isEmpty()) {
                    // Use LiveData instead of Toast
                    _messageEvent.postValue("No transactions found for category: " + category);
                    if (onNoResults != null) onNoResults.run();
                    return; // ❌ don’t update LiveData
                }
            }

            if (type.equals("Income")) incomeLive.postValue(filtered);
            else expenseLive.postValue(filtered);

            new android.os.Handler(getApplication().getMainLooper()).post(() -> {
                if (onSuccess != null) onSuccess.run();
            });

        }).start();
    }

    private final MutableLiveData<String> _messageEvent = new MutableLiveData<>();
    public LiveData<String> getMessageEvent() { return _messageEvent; }
}
