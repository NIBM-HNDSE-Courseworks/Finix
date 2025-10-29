package com.example.finix.ui.dashboard;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.finix.data.Category;
import com.example.finix.data.CategoryDAO;
import com.example.finix.data.FinixDatabase;
import com.example.finix.data.Transaction;
import com.example.finix.data.TransactionDao;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardViewModel extends AndroidViewModel {

    private final TransactionDao transactionDao;
    private final CategoryDAO categoryDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat monthYearFormatter = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
    private final SimpleDateFormat monthFormatter = new SimpleDateFormat("MMMM", Locale.getDefault());

    // 1. Month Picker Data
    private final MutableLiveData<List<String>> distinctMonthsLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> selectedMonthYearLive = new MutableLiveData<>();

    // Dedicated LiveData to force a refresh, even if the selected month string hasn't changed.
    private final MutableLiveData<Void> forceRefreshTrigger = new MutableLiveData<>();

    // Status to indicate if any transactions exist at all.
    private final MutableLiveData<Boolean> hasTransactionsMutableLive = new MutableLiveData<>(false);
    public final LiveData<Boolean> hasTransactionsLive = hasTransactionsMutableLive;

    // Helper to get selected month's range (start and end timestamps)
    private final MediatorLiveData<long[]> dateRangeMediatorLive = new MediatorLiveData<>();
    private final LiveData<long[]> dateRangeLive = dateRangeMediatorLive; // Public reference point

    // 2. Data Structures for UI (Updated to use MutableLiveData for totals)
    private final MutableLiveData<Double> incomeTotalMutableLive = new MutableLiveData<>();
    public final LiveData<Double> incomeTotalLive = incomeTotalMutableLive;

    private final MutableLiveData<Double> expenseTotalMutableLive = new MutableLiveData<>();
    public final LiveData<Double> expenseTotalLive = expenseTotalMutableLive;

    public final LiveData<String> incomeComparisonLive;
    public final LiveData<String> expenseComparisonLive;

    // 3. Data for Charts (Uses LiveData DAO method)
    public final LiveData<List<Transaction>> monthlyIncomeTransactionsLive;
    public final LiveData<List<Transaction>> monthlyExpenseTransactionsLive;

    // Chart Data
    private final MutableLiveData<Map<Integer, String>> categoryMapLive = new MutableLiveData<>(new HashMap<>());

    public DashboardViewModel(@NonNull Application application, CategoryDAO categoryDao) {
        super(application);
        transactionDao = FinixDatabase.getDatabase(application).transactionDao();
        this.categoryDao = categoryDao;
        loadDistinctMonths();
        loadCategories();

        // --- Date Range Mediator Setup (For month changes and pull-to-refresh) ---
        // 1. Listen to month selection change (normal operation)
        dateRangeMediatorLive.addSource(selectedMonthYearLive, month -> {
            dateRangeMediatorLive.setValue(getMonthDateRange(month));
        });

        // 2. Listen to the explicit refresh trigger
        dateRangeMediatorLive.addSource(forceRefreshTrigger, aVoid -> {
            String currentMonth = selectedMonthYearLive.getValue();
            if (currentMonth != null) {
                dateRangeMediatorLive.setValue(getMonthDateRange(currentMonth));
            }
        });
        // -----------------------------------------------------

        // --- Transformations for Transactions (Now using the fixed dateRangeLive) ---
        monthlyIncomeTransactionsLive = Transformations.switchMap(dateRangeLive, range ->
                transactionDao.getTransactionsByTypeAndDateRange("Income", range[0], range[1]));

        monthlyExpenseTransactionsLive = Transformations.switchMap(dateRangeLive, range ->
                transactionDao.getTransactionsByTypeAndDateRange("Expense", range[0], range[1]));

        // ⭐ CRITICAL FIX: Mediator to trigger Totals calculation on date change OR new data (transactions)
        MediatorLiveData<long[]> totalCalculationTrigger = new MediatorLiveData<>();

        // Source 1: Triggers when the month/date range changes
        totalCalculationTrigger.addSource(dateRangeLive, totalCalculationTrigger::setValue);

        // Source 2 & 3: Triggers when the underlying transaction data changes (i.e., new transaction added)
        // We set the current range again to force the total calculation to run.
        totalCalculationTrigger.addSource(monthlyIncomeTransactionsLive, transactions -> {
            long[] currentRange = dateRangeLive.getValue();
            if (currentRange != null) totalCalculationTrigger.setValue(currentRange);
        });
        totalCalculationTrigger.addSource(monthlyExpenseTransactionsLive, transactions -> {
            long[] currentRange = dateRangeLive.getValue();
            if (currentRange != null) totalCalculationTrigger.setValue(currentRange);
        });

        // Observer: Executes the synchronous total queries on a background thread
        totalCalculationTrigger.observeForever(range -> {
            if (range != null) {
                calculateMonthlyTotal("Income", range[0], range[1], incomeTotalMutableLive);
                calculateMonthlyTotal("Expense", range[0], range[1], expenseTotalMutableLive);
            }
        });
        // -----------------------------------------------------------------------------

        // --- Transformations for Comparison Text (These fire correctly when income/expense totals update) ---
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

    /**
     * Executes the synchronous total query on a background thread and posts the result.
     */
    private void calculateMonthlyTotal(String type, long startTime, long endTime, MutableLiveData<Double> totalLive) {
        executor.execute(() -> {
            // Uses the synchronous DAO method: getPreviousMonthTotalSync
            Double total = transactionDao.getPreviousMonthTotalSync(type, startTime, endTime);
            totalLive.postValue(total != null ? total : 0.0);
        });
    }

    // --- Public Getters for Fragment ---

    public LiveData<List<String>> getDistinctMonthsLive() {
        return distinctMonthsLive;
    }

    public LiveData<String> getSelectedMonthYearLive() {
        return selectedMonthYearLive;
    }

    public LiveData<Map<Integer, String>> getCategoryMapLive() {
        return categoryMapLive;
    }

    // NEW Getter for the overall data status
    public LiveData<Boolean> getHasTransactionsLive() {
        return hasTransactionsLive;
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
            // Uses the new DAO method: getDistinctMonthYear()
            List<Long> timestamps = transactionDao.getDistinctMonthYear();
            List<String> months = new ArrayList<>();

            // NEW: Set the hasTransactions status
            hasTransactionsMutableLive.postValue(!timestamps.isEmpty());

            String lastMonthYear = null;
            Calendar cal = Calendar.getInstance();

            for (Long timestamp : timestamps) {
                if (timestamp != null) {
                    cal.setTimeInMillis(timestamp);
                    String currentMonthYear = monthYearFormatter.format(cal.getTime());

                    if (!currentMonthYear.equals(lastMonthYear)) {
                        months.add(currentMonthYear);
                        lastMonthYear = currentMonthYear;
                    }
                }
            }
            distinctMonthsLive.postValue(months);
            // Initialize selected month to the newest one
            if (!months.isEmpty() && selectedMonthYearLive.getValue() == null) {
                selectedMonthYearLive.postValue(months.get(0));
            }
        });
    }

    /**
     * Updates the selected month and triggers all transformations.
     * @param monthYear The selected month/year string.
     */
    public void setSelectedMonth(String monthYear) {
        if (monthYear != null) {
            if (!monthYear.equals(selectedMonthYearLive.getValue())) {
                // Normal selection: month changed, update value
                selectedMonthYearLive.setValue(monthYear);
            } else {
                // Explicit Refresh: month is the same, so we trigger the force refresh LiveData
                // This ensures the date range re-calculates, re-triggering the switchMaps (DAO queries).
                forceRefreshTrigger.setValue(null);
            }
        }
    }

    /**
     * Calculates the comparison text (e.g., "Increased +Rs. 1,000 from last month").
     */
    private void calculateComparison(LiveData<Double> currentTotalLive, String type, MediatorLiveData<String> comparisonLive) {
        executor.execute(() -> {
            List<String> distinctMonths = distinctMonthsLive.getValue();

            // Check if there are NO transactions at all.
            if (distinctMonths == null || distinctMonths.isEmpty()) {
                String noDataText = "No transactions saved yet.";
                String color = "#607D8B"; // Neutral grey
                comparisonLive.postValue(color + "|" + noDataText);
                return;
            }

            String selectedMonth = selectedMonthYearLive.getValue();
            // Get the current total synchronously (safe because we are on a background thread)
            Double currentTotal = currentTotalLive.getValue();


            if (selectedMonth == null || currentTotal == null) {
                // Should only happen transiently or if selectedMonthYearLive wasn't initialized
                comparisonLive.postValue(null);
                return;
            }

            // 1. Find the index of the selected month
            int selectedIndex = distinctMonths.indexOf(selectedMonth);

            // --- CRITICAL FIX: Handle case where no previous month exists ---
            if (selectedIndex == distinctMonths.size() - 1) {
                // This is the first recorded month, so there's nothing to compare to.
                String noDataText = "First month with data recorded";
                String color = "#607D8B"; // Neutral grey
                comparisonLive.postValue(color + "|" + noDataText);
                return;
            }
            // -----------------------------------------------------------------


            // 2. Determine the previous recorded month
            Double prevTotal = 0.0;
            String prevMonthName = "last month";

            // If we are not at the end of the list, a previous month exists (since the list is newest first)
            if (selectedIndex != -1 && selectedIndex + 1 < distinctMonths.size()) {
                String previousMonthYear = distinctMonths.get(selectedIndex + 1);

                // Get the date range for the previous month
                long[] prevRange = getMonthDateRange(previousMonthYear);

                // Uses the synchronous DAO method: getPreviousMonthTotalSync
                Double prevTotalResult = transactionDao.getPreviousMonthTotalSync(type, prevRange[0], prevRange[1]);
                prevTotal = (prevTotalResult != null) ? prevTotalResult : 0.0;

                try {
                    prevMonthName = monthFormatter.format(monthYearFormatter.parse(previousMonthYear));
                } catch (Exception e) {
                    // Fallback
                }
            }

            // 3. Calculate difference
            double difference = currentTotal - prevTotal;
            String sign = difference >= 0 ? "+" : "-";

            String comparisonText;
            String color;

            if (Math.abs(difference) < 0.01) {
                comparisonText = "No change compared to " + prevMonthName;
                color = "#607D8B"; // Neutral grey
            } else {
                String action = difference > 0 ? "Increased" : "Decreased";

                // CRITICAL FIX: Invert color logic for Expenses
                if (type.equals("Income")) {
                    // Income: Higher is GOOD (Teal/Green), Lower is BAD (Red)
                    color = difference > 0 ? "#00BFA5" : "#E57373";
                } else { // type.equals("Expense")
                    // Expense: Lower is GOOD (Teal/Green), Higher is BAD (Red)
                    color = difference < 0 ? "#00BFA5" : "#E57373";
                }

                // Ensure correct formatting
                String formattedDifference = String.format(Locale.getDefault(), "%.2f", Math.abs(difference));
                comparisonText = String.format("%s %sRs. %s from %s", action, sign, formattedDifference, prevMonthName);
            }

            // 4. Post the value to LiveData (color|text format for view)
            comparisonLive.postValue(color + "|" + comparisonText);
        });
    }

    private void loadCategories() {
        executor.execute(() -> {
            try {
                // Assuming CategoryDAO has getAllCategories() which is synchronous
                List<Category> categories = categoryDao.getAllCategories();
                Map<Integer, String> categoryMap = new HashMap<>();
                for (Category cat : categories) {
                    categoryMap.put(cat.getId(), cat.getName());
                }
                categoryMapLive.postValue(categoryMap);
            } catch (Exception e) {
                // Log and handle error gracefully
                System.err.println("Error loading categories: " + e.getMessage());
            }
        });
    }
}
