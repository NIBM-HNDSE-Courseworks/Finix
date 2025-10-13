package com.example.finix.ui.transactions;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.finix.data.Category;
import com.example.finix.data.CategoryDAO;
import com.example.finix.data.FinixDatabase;
import com.example.finix.data.Transaction;

import java.util.ArrayList;
import java.util.List;

public class TransactionsViewModel extends AndroidViewModel {

    private final MutableLiveData<String> mText;
    private final MutableLiveData<List<String>> categoriesLive;
    private final CategoryDAO categoryDao;

    public TransactionsViewModel(@NonNull Application app) {
        super(app);
        mText = new MutableLiveData<>();
        mText.setValue("Transactions View: Enter a new expense or view recent activity here.");

        categoriesLive = new MutableLiveData<>(new ArrayList<>());

        FinixDatabase db = FinixDatabase.getDatabase(app);
        categoryDao = db.categoryDao();

        loadCategories(); // Load saved categories from DB
    }

    public LiveData<String> getText() { return mText; }

    public LiveData<List<String>> getCategoriesLive() { return categoriesLive; }

    public void loadCategories() {
        new Thread(() -> {
            List<Category> list = categoryDao.getAllCategories();
            List<String> names = new ArrayList<>();
            for (Category c : list) names.add(c.getName());
            names.add("Add New Category"); // special option
            categoriesLive.postValue(names);
        }).start();
    }

    public void addCategory(String newCategory) {
        if (newCategory == null || newCategory.trim().isEmpty()) return;

        new Thread(() -> {
            Category cat = new Category(newCategory.trim());
            categoryDao.insert(cat);
            loadCategories(); // refresh LiveData
        }).start();
    }

    public void saveTransaction(double amount, String type, String category, long dateTime, String description) {
        new Thread(() -> {
            Transaction transaction = new Transaction(amount, type, category, dateTime, description);
            FinixDatabase db = FinixDatabase.getDatabase(getApplication());
            db.transactionDao().insert(transaction);

            // Show Toast after saving (run on main thread)
            android.os.Handler mainHandler = new android.os.Handler(getApplication().getMainLooper());
            mainHandler.post(() -> {
                android.widget.Toast.makeText(
                        getApplication(),
                        "💾 Transaction saved to: " + db.getOpenHelper().getWritableDatabase().getPath(),
                        android.widget.Toast.LENGTH_LONG
                ).show();
            });
        }).start();
    }

}
