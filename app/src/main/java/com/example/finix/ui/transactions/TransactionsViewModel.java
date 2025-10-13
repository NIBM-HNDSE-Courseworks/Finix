package com.example.finix.ui.transactions;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * ViewModel for the Transactions Fragment.
 * This class holds and manages the data required by the TransactionsFragment (the View).
 * It survives configuration changes (like screen rotation).
 */
public class TransactionsViewModel extends ViewModel {

    // MutableLiveData holds the data that can be changed by the ViewModel
    // and observed (watched) by the Fragment.
    private final MutableLiveData<String> mText;

    // In a real app, this would hold lists of transactions, running balances, etc.,
    // and methods to interact with the Repository (for SQLite/Oracle access).
    // private MutableLiveData<List<Transaction>> transactionsList;

    public TransactionsViewModel() {
        // Initialize the MutableLiveData objects
        mText = new MutableLiveData<>();

        // Set some default text that the Fragment will display upon creation
        // In a real app, you would initiate data loading here (e.g., fetch recent transactions).
        mText.setValue("Transactions View: Enter a new expense or view recent activity here.");

        // transactionsList = new MutableLiveData<>();
        // loadTransactions();
    }

    /**
     * Exposes the data as LiveData (read-only) to the Fragment.
     * The Fragment observes this LiveData to update its UI when the data changes.
     *
     * @return LiveData object containing the text to display.
     */
    public LiveData<String> getText() {
        return mText;
    }

    /*
    // Placeholder for a future method to load data from a Repository (SQLite/Oracle)
    private void loadTransactions() {
        // Example: Logic to fetch transactions from SQLite or Oracle DB
        // ...
        // transactionsList.setValue(fetchedList);
    }

    // Placeholder for adding a new transaction
    public void addTransaction(double amount, String description, boolean isExpense) {
        // 1. Validate data
        // 2. Pass data to a Repository for storage in SQLite
        // 3. Update the LiveData to refresh the UI
    }
    */
}
