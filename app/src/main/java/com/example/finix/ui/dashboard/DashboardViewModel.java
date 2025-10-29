package com.example.finix.ui.dashboard;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import com.example.finix.data.FinixDatabase;
import com.example.finix.data.Transaction;
import com.example.finix.data.TransactionDao;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class DashboardViewModel extends AndroidViewModel {

    private final TransactionDao transactionDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat monthYearFormatter = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
    private final SimpleDateFormat monthFormatter = new SimpleDateFormat("MMMM", Locale.getDefault());

    // 1. Month Picker Data
    private final MutableLiveData<List<String>> distinctMonthsLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> selectedMonthYearLive = new MutableLiveData<>();

    // 2. Data Structures for UI
    public final LiveData<Double> incomeTotalLive;
    public final LiveData<String> incomeComparisonLive;
    public final LiveData<Double> expenseTotalLive;
    public final LiveData<String> expenseComparisonLive;

    // 3. Data for Charts
    public final LiveData<List<Transaction>> monthlyIncomeTransactionsLive;
    public final LiveData<List<Transaction>> monthlyExpenseTransactionsLive;

    // Helper to get selected month's range (start and end timestamps)
    private final LiveData<long[]> dateRangeLive = Transformations.map(selectedMonthYearLive, this::getMonthDateRange);

    public DashboardViewModel(@NonNull Application application) {
        super(application);
        transactionDao = FinixDatabase.getDatabase(application).transactionDao();
        loadDistinctMonths(); // Load initial list of months

        // --- Transformations for Totals and Transactions ---
        // This is a powerful MVVM pattern: whenever dateRangeLive changes, the underlying database query changes.

        incomeTotalLive = Transformations.switchMap(dateRangeLive, range ->
                transactionDao.getTotalAmountByTypeAndDateRange("Income", range[0], range[1]));

        expenseTotalLive = Transformations.switchMap(dateRangeLive, range ->
                transactionDao.getTotalAmountByTypeAndDateRange("Expense", range[0], range[1]));

        monthlyIncomeTransactionsLive = Transformations.switchMap(dateRangeLive, range ->
                transactionDao.getTransactionsByTypeAndDateRange("Income", range[0], range[1]));

        monthlyExpenseTransactionsLive = Transformations.switchMap(dateRangeLive, range ->
                transactionDao.getTransactionsByTypeAndDateRange("Expense", range[0], range[1]));

        // --- Transformations for Comparison Text ---
        // Comparison requires knowing the selected month and the total from the previous recorded month.
        incomeComparisonLive = new MediatorLiveData<>();
        expenseComparisonLive = new MediatorLiveData<>();

        // Use a single LiveData to trigger comparison calculation when either the month or total changes
        MediatorLiveData<Void> triggerComparison = new MediatorLiveData<>();
        triggerComparison.addSource(selectedMonthYearLive, s -> triggerComparison.setValue(null));
        triggerComparison.addSource(incomeTotalLive, s -> triggerComparison.setValue(null));
        triggerComparison.addSource(expenseTotalLive, s -> triggerComparison.setValue(null));

        // Now, observe the trigger to perform calculations
        triggerComparison.observeForever(aVoid -> {
            calculateComparison(incomeTotalLive, "Income", (MediatorLiveData<String>) incomeComparisonLive);
            calculateComparison(expenseTotalLive, "Expense", (MediatorLiveData<String>) expenseComparisonLive);
        });
    }

    // --- Public Getters for Fragment ---

    public LiveData<List<String>> getDistinctMonthsLive() {
        return distinctMonthsLive;
    }

    public LiveData<String> getSelectedMonthYearLive() {
        return selectedMonthYearLive;
    }

    // --- Core Logic Methods ---

    /**
     * Finds the start and end timestamps for the given "MMMM yyyy" string.
     * @param monthYearString The month and year (e.g., "October 2025").
     * @return A long array: [0] = start timestamp, [1] = end timestamp.
     */
    private long[] getMonthDateRange(String monthYearString) {
        if (monthYearString == null) return new long[]{0, Long.MAX_VALUE};
        try {
            Date date = monthYearFormatter.parse(monthYearString);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);

            // Set to the first millisecond of the month
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startTime = calendar.getTimeInMillis();

            // Set to the last millisecond of the month
            calendar.add(Calendar.MONTH, 1);
            calendar.add(Calendar.MILLISECOND, -1);
            long endTime = calendar.getTimeInMillis();

            return new long[]{startTime, endTime};
        } catch (Exception e) {
            // Handle parsing error by returning a range that won't find anything
            return new long[]{0, 0};
        }
    }

    /**
     * Loads all distinct month/year strings from transactions (newest first).
     */
    private void loadDistinctMonths() {
        executor.execute(() -> {
            List<Long> timestamps = transactionDao.getDistinctMonthYear(); //
            List<String> months = new ArrayList<>();

            // Use Calendar and SimpleDateFormat to get unique "MMMM yyyy" strings
            String lastMonthYear = null;
            Calendar cal = Calendar.getInstance();

            for (Long timestamp : timestamps) {
                if (timestamp != null) {
                    cal.setTimeInMillis(timestamp);
                    String currentMonthYear = monthYearFormatter.format(cal.getTime());

                    // Only add the month/year if it's different from the previous one
                    if (!currentMonthYear.equals(lastMonthYear)) {
                        months.add(currentMonthYear);
                        lastMonthYear = currentMonthYear;
                    }
                }
            }
            distinctMonthsLive.postValue(months);
        });
    }

    /**
     * Updates the selected month and triggers all transformations.
     * @param monthYear The selected month/year string.
     */
    public void setSelectedMonth(String monthYear) {
        if (!monthYear.equals(selectedMonthYearLive.getValue())) {
            selectedMonthYearLive.setValue(monthYear);
        }
    }

    /**
     * Calculates the comparison text (e.g., "Increased +$1,000 from last month").
     */
    private void calculateComparison(LiveData<Double> currentTotalLive, String type, MediatorLiveData<String> comparisonLive) {
        executor.execute(() -> {
            String selectedMonth = selectedMonthYearLive.getValue();
            Double currentTotal = currentTotalLive.getValue();
            List<String> distinctMonths = distinctMonthsLive.getValue();

            if (selectedMonth == null || currentTotal == null || distinctMonths == null || distinctMonths.isEmpty()) {
                comparisonLive.postValue(null);
                return;
            }

            // 1. Find the index of the selected month
            int selectedIndex = distinctMonths.indexOf(selectedMonth);

            // 2. Determine the previous recorded month (next in the descending list)
            Double prevTotal = 0.0;
            String prevMonthName = "last month";

            if (selectedIndex != -1 && selectedIndex + 1 < distinctMonths.size()) {
                String previousMonthYear = distinctMonths.get(selectedIndex + 1);

                // Get the date range for the previous month
                long[] prevRange = getMonthDateRange(previousMonthYear);

                // Wait synchronously for the previous month's total from the DB
                prevTotal = transactionDao.getTotalAmountByTypeAndDateRange(type, prevRange[0], prevRange[1]).getValue();
                prevTotal = (prevTotal != null) ? prevTotal : 0.0;

                try {
                    prevMonthName = monthFormatter.format(monthYearFormatter.parse(previousMonthYear));
                } catch (Exception e) {
                    // Fallback to "last month"
                }
            } else if (distinctMonths.size() > 1) {
                // If the selected month is the *newest* one, and there is a previous one (size > 1),
                // it's already covered by index + 1
            }

            // 3. Calculate difference
            double difference = currentTotal - prevTotal;
            String sign = difference >= 0 ? "+" : "-";
            String color = difference >= 0 ? "#00BFA5" : "#FF5252"; // Teal/Red

            String comparisonText;
            if (Math.abs(difference) < 0.01) {
                comparisonText = "No change compared to " + prevMonthName;
                color = "#607D8B"; // Neutral grey
            } else {
                String action = difference > 0 ? "Increased" : "Decreased";
                String absDifference = String.format(Locale.getDefault(), "%.2f", Math.abs(difference));
                comparisonText = String.format("%s %s$%.2f from %s", action, sign, Math.abs(difference), prevMonthName);
            }

            // 4. Post the value to LiveData (with color hint if needed)
            comparisonLive.postValue(color + "|" + comparisonText);
        });
    }
}