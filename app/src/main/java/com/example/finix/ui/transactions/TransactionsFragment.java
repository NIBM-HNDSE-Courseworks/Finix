package com.example.finix.ui.transactions;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputFilter;
import android.util.Log; // <-- DEBUGGING: Import Log class
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer; // Import for explicit observer
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.finix.R;
import com.example.finix.data.Transaction;
import com.example.finix.databinding.FragmentTransactionsBinding;
import java.text.SimpleDateFormat;
import java.util.*;

public class TransactionsFragment extends Fragment {

    // DEBUGGING: Define a TAG for logging
    private static final String TAG = "Finix_TransFragment";

    private FragmentTransactionsBinding binding;
    private TransactionsViewModel viewModel;
    private TransactionAdapter incomeAdapter, expenseAdapter;

    // 🆕 NEW: Variable to hold the current list of available month/years
    private final List<String> distinctMonths = new ArrayList<>();

    // 🆕 NEW: Variable to store the currently selected filter month/year
    private String currentMonthYearFilter = null;

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Fragment becoming visible. Checking filter status for reload.");

        // Check if a month filter is currently active (set either by user or by default logic).
        if (currentMonthYearFilter != null) {
            // If an active filter exists, re-run it to ensure data is fresh and the filter is maintained.
            Log.d(TAG, "onResume: Re-applying current month filter: " + currentMonthYearFilter);
            viewModel.filterByMonthYear(currentMonthYearFilter);
        } else {
            // If no filter is active (i.e., "Show All" is active), reload all transactions.
            // This also ensures the ViewModel's distinct months are up-to-date, which triggers
            // the logic in updateMonthYearSpinner to set the default current month.
            Log.d(TAG, "onResume: No month filter active. Reloading all transactions.");
            viewModel.loadAllTransactions();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        Log.d(TAG, "onCreateView: Starting fragment initialization.");

        viewModel = new ViewModelProvider(this).get(TransactionsViewModel.class);
        binding = FragmentTransactionsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // 🔹 Setup RecyclerViews
        incomeAdapter = new TransactionAdapter();
        expenseAdapter = new TransactionAdapter();

        LinearLayoutManager incomeLayout = new LinearLayoutManager(getContext());
        LinearLayoutManager expenseLayout = new LinearLayoutManager(getContext());
        binding.recyclerIncome.setLayoutManager(incomeLayout);
        binding.recyclerExpenses.setLayoutManager(expenseLayout);

        binding.recyclerIncome.setAdapter(incomeAdapter);
        binding.recyclerExpenses.setAdapter(expenseAdapter);
        Log.d(TAG, "onCreateView: RecyclerViews and adapters initialized.");

        // 🔹 Add spacing between items
        try {
            int spacing = getResources().getDimensionPixelSize(R.dimen.transaction_item_spacing);
            RecyclerView.ItemDecoration decoration = new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                           @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    outRect.bottom = spacing;
                }
            };
            binding.recyclerIncome.addItemDecoration(decoration);
            binding.recyclerExpenses.addItemDecoration(decoration);
            Log.d(TAG, "onCreateView: Item spacing added. Spacing value: " + spacing);
        } catch (Exception e) {
            Log.e(TAG, "onCreateView: Failed to get transaction_item_spacing dimension.", e);
        }

        // 🔹 Observe transactions
        viewModel.getIncomeTransactions().observe(getViewLifecycleOwner(), list -> {
            Log.d(TAG, "Income LiveData updated. Count: " + (list != null ? list.size() : 0));
            incomeAdapter.setTransactions(list);
            updateTransactionVisibility();
        });
        viewModel.getExpenseTransactions().observe(getViewLifecycleOwner(), list -> {
            Log.d(TAG, "Expense LiveData updated. Count: " + (list != null ? list.size() : 0));
            expenseAdapter.setTransactions(list);
            updateTransactionVisibility();
        });

        viewModel.getMessageEvent().observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                Log.i(TAG, "MessageEvent received: " + message);
                showCustomToast(message);
            }
        });

        // 🔹 Keep category map updated in adapters
        viewModel.getCategoriesLive().observe(getViewLifecycleOwner(), map -> {
            int count = (map != null) ? map.size() : 0;
            Log.d(TAG, "Category LiveData updated for adapters. Category Count: " + count);
            if (map != null) {
                incomeAdapter.setCategoryMap(map);
                expenseAdapter.setCategoryMap(map);
                incomeAdapter.notifyDataSetChanged();
                expenseAdapter.notifyDataSetChanged();
            }
        });

        // 🔹 🆕 NEW: Observe distinct months/years for the Spinner
        viewModel.getDistinctMonthsLive().observe(getViewLifecycleOwner(), months -> {
            Log.d(TAG, "Distinct Months LiveData updated. Count: " + (months != null ? months.size() : 0));
            updateMonthYearSpinner(months);
        });

        // 🔹 Add transaction
        binding.buttonAddTransaction.setOnClickListener(v -> {
            Log.d(TAG, "Add Transaction button clicked. Showing Add/Edit dialog.");
            showAddTransactionDialog(null);
        });

        // 🔹 🆕 NEW: Set up Month/Year Spinner Listener
        setupMonthYearSpinner();

        // 🔹 Filters
        binding.buttonFilterIncome.setOnClickListener(v -> showFilterMenu("Income"));
        binding.buttonFilterExpenses.setOnClickListener(v -> showFilterMenu("Expense"));

        // 🔹 Edit & Delete buttons inside adapter
        incomeAdapter.setListener(new TransactionAdapter.OnTransactionActionListener() {
            @Override
            public void onEdit(Transaction t) {
                Log.d(TAG, "Income transaction EDIT clicked for ID: " + t.getId());
                showAddTransactionDialog(t);
            }

            @Override
            public void onDelete(Transaction t) {
                Log.d(TAG, "Income transaction DELETE clicked for ID: " + t.getId());
                showDeleteConfirmation(t);
            }
        });

        expenseAdapter.setListener(new TransactionAdapter.OnTransactionActionListener() {
            @Override
            public void onEdit(Transaction t) {
                Log.d(TAG, "Expense transaction EDIT clicked for ID: " + t.getId());
                showAddTransactionDialog(t);
            }

            @Override
            public void onDelete(Transaction t) {
                Log.d(TAG, "Expense transaction DELETE clicked for ID: " + t.getId());
                showDeleteConfirmation(t);
            }
        });

        Log.d(TAG, "onCreateView: Listeners set up successfully.");

        return root;
    }

    // 🆕 NEW: Setup the Spinner and its listener
    private void setupMonthYearSpinner() {
        binding.spinnerMonthYear.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    // "Show All" selected
                    if (currentMonthYearFilter != null) {
                        Log.i(TAG, "Spinner selection: Show All.");
                        viewModel.loadAllTransactions();
                        currentMonthYearFilter = null;
                    }
                } else {
                    // A specific month/year is selected
                    String selectedMonthYear = distinctMonths.get(position - 1);
                    if (!Objects.equals(selectedMonthYear, currentMonthYearFilter)) {
                        Log.i(TAG, "Spinner selection: Filtering by " + selectedMonthYear);
                        viewModel.filterByMonthYear(selectedMonthYear);
                        currentMonthYearFilter = selectedMonthYear;
                    }
                }

                // ❌ REMOVED: Custom text color logic is gone
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
        Log.d(TAG, "setupMonthYearSpinner: Listener attached.");
    }

    // 🆕 UPDATED: Update the Spinner's data and set the default current month filter.
    private void updateMonthYearSpinner(List<String> months) {
        distinctMonths.clear();
        if (months != null) {
            // ASSUMPTION: 'months' list is sorted in descending chronological order (newest month first).
            distinctMonths.addAll(months);
        }

        // 1. Create the final list for the adapter
        List<String> adapterList = new ArrayList<>();
        adapterList.add("Show All"); // The default, first option
        adapterList.addAll(distinctMonths); // Add the actual month/year values

        // 2. Create and set the adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                adapterList);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerMonthYear.setAdapter(adapter);

        // 3. Set the selection and apply filter if needed
        // Check if there's no current filter AND at least one month is available (adapterList size > 1)
        boolean isInitialDefaultSet = currentMonthYearFilter == null && adapterList.size() > 1;

        if (isInitialDefaultSet) {
            // --- LOGIC for initial default selection: Select the latest month ---

            // Select the latest month, which is at index 1 (after "Show All").
            final int LATEST_MONTH_INDEX = 1;
            binding.spinnerMonthYear.setSelection(LATEST_MONTH_INDEX);

            // Manually apply the filter for the default month since setSelection doesn't
            // trigger onItemSelected on initial load.
            String defaultSelection = adapterList.get(LATEST_MONTH_INDEX);
            viewModel.filterByMonthYear(defaultSelection); // Apply filter
            currentMonthYearFilter = defaultSelection;     // Store filter state

            Log.i(TAG, "updateMonthYearSpinner: Default selection set to latest month: " + defaultSelection);
        } else if (currentMonthYearFilter != null) {
            // Maintain the currently active filter (after resume or when a month is selected)
            int index = distinctMonths.indexOf(currentMonthYearFilter);
            if (index != -1) {
                binding.spinnerMonthYear.setSelection(index + 1); // +1 because of "Show All"
                Log.d(TAG, "updateMonthYearSpinner: Maintaining current filter: " + currentMonthYearFilter + " at index " + (index + 1));
            } else {
                // Active filter disappeared (e.g., all transactions in that month were deleted)
                binding.spinnerMonthYear.setSelection(0);
                currentMonthYearFilter = null;
                Log.w(TAG, "updateMonthYearSpinner: Active filter disappeared. Resetting to 'Show All'.");
                viewModel.loadAllTransactions(); // Force load all transactions
            }
        } else {
            // Default to "Show All" if no transactions exist.
            binding.spinnerMonthYear.setSelection(0);
            Log.d(TAG, "updateMonthYearSpinner: Defaulting/Resetting to 'Show All'.");
        }

        // Hide the spinner if there are no months (only 'Show All')
        binding.spinnerMonthYear.setVisibility(adapterList.size() > 1 ? View.VISIBLE : View.GONE);
        Log.d(TAG, "updateMonthYearSpinner: Adapter set. Items: " + adapterList.size());
    }

    // --- NEW METHOD FOR CUSTOM DELETE CONFIRMATION ---
    private void showDeleteConfirmation(Transaction t) {
        Log.d(TAG, "showDeleteConfirmation: Preparing dialog for transaction ID: " + t.getId());
        View popupView = requireActivity().getLayoutInflater().inflate(R.layout.delete_confirmation_popup, null);

        TextView tvMessage = popupView.findViewById(R.id.deleteMessage);

        if (tvMessage != null) {
            String categoryName = viewModel.getCategoryMap().getOrDefault(t.getCategoryId(), "a transaction");
            String message = "Are you sure you want to delete the " + t.getType().toLowerCase() + " of Rs. " + String.format("%.2f", t.getAmount()) + " for '" + categoryName + "'?";
            tvMessage.setText(message);
            Log.d(TAG, "showDeleteConfirmation: Message set: " + message);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(popupView).create();
        dialog.show();

        Window window = dialog.getWindow();
        if(window != null){
            int dialogWidth = (int)(requireActivity().getResources().getDisplayMetrics().widthPixels * 0.8);
            window.setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            Log.d(TAG, "showDeleteConfirmation: Dialog size and background set.");
        }

        Button cancelBtn = popupView.findViewById(R.id.cancelDeleteBtn);
        Button confirmBtn = popupView.findViewById(R.id.confirmDeleteBtn);

        cancelBtn.setOnClickListener(v -> {
            Log.d(TAG, "Delete dialog: Cancel clicked.");
            dialog.dismiss();
        });

        confirmBtn.setOnClickListener(v -> {
            Log.i(TAG, "Delete dialog: Confirm clicked. Deleting transaction ID: " + t.getId());
            dialog.dismiss();
            viewModel.deleteTransaction(t);
        });
    }


    // 🔹 Popup for Add/Edit Transaction
    private void showAddTransactionDialog(Transaction transactionToEdit) {
        if (transactionToEdit != null) {
            Log.i(TAG, "showAddTransactionDialog: Editing existing transaction ID: " + transactionToEdit.getId());
        } else {
            Log.i(TAG, "showAddTransactionDialog: Adding new transaction.");
        }

        LayoutInflater inflater = LayoutInflater.from(getContext());
        View popupView = inflater.inflate(R.layout.add_edit_transaction_popup, null);

        // --- View Initialization ---
        TextView tvTitle = popupView.findViewById(R.id.popupTitle);
        tvTitle.setText(transactionToEdit != null ? "Edit Transaction" : "Add Transaction");

        EditText etAmount = popupView.findViewById(R.id.etAmount);
        EditText etDescription = popupView.findViewById(R.id.etDescription);
        Button btnSave = popupView.findViewById(R.id.btnSaveTransaction);
        ImageButton btnPickDateTime = popupView.findViewById(R.id.btnPickDateTime);
        TextView tvDateTime = popupView.findViewById(R.id.tvDateTime);
        RadioGroup rgType = popupView.findViewById(R.id.rgType);

        AutoCompleteTextView actCategory = popupView.findViewById(R.id.actCategory);
        LinearLayout llAddCategory = popupView.findViewById(R.id.llAddNewCategory);
        EditText etNewCategory = popupView.findViewById(R.id.etNewCategory);
        Button btnSaveCategory = popupView.findViewById(R.id.btnSaveCategory);
        Button btnBackCategory = popupView.findViewById(R.id.btnCancelCategory);

        // 🟢 ADD CHARACTER LIMITS HERE
        etAmount.setFilters(new InputFilter[] { new InputFilter.LengthFilter(7) });
        etNewCategory.setFilters(new InputFilter[] { new InputFilter.LengthFilter(14) });
        Log.d(TAG, "Dialog: Input filters (limits) applied.");

        // --- Category setup ---
        List<String> categoriesList = new ArrayList<>();
        Map<String, Integer> categoryNameToIdMap = new HashMap<>();

        // Ensure "Add New Category" is the first entry
        categoriesList.add("Add New Category");

        // Initial pre-population of categories from ViewModel's current state
        Map<Integer, String> currentCategories = viewModel.getCategoryMap();
        if (currentCategories != null) {
            for (Map.Entry<Integer, String> entry : currentCategories.entrySet()) {
                categoriesList.add(entry.getValue());
                categoryNameToIdMap.put(entry.getValue(), entry.getKey());
            }
        }
        Log.d(TAG, "Dialog: Initial category list size: " + categoriesList.size());


        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_dropdown_item_1line, categoriesList);
        actCategory.setAdapter(adapter);
        actCategory.setThreshold(0);

        // Force refresh on focus and click!
        actCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                Log.d(TAG, "Category AutoCompleteTextView gained focus. Refreshing categories.");
                refreshCategories(adapter, categoriesList, categoryNameToIdMap, actCategory);
                actCategory.post(() -> {
                    actCategory.showDropDown(); // Always show latest items
                });
            }
        });

        actCategory.setOnClickListener(v -> {
            if (!actCategory.isPopupShowing()) {
                Log.d(TAG, "Category AutoCompleteTextView clicked. Refreshing categories.");
                refreshCategories(adapter, categoriesList, categoryNameToIdMap, actCategory);
                actCategory.post(() -> {
                    actCategory.showDropDown(); // Always show latest items
                });
            }
        });

        actCategory.setOnItemClickListener((parent, view, position, id) -> {
            String selected = adapter.getItem(position);
            Log.d(TAG, "Dialog: Category selected: " + selected);
            if ("Add New Category".equals(selected)) {
                llAddCategory.setVisibility(View.VISIBLE);
                actCategory.setVisibility(View.GONE);
                etNewCategory.requestFocus();
                Log.d(TAG, "Dialog: 'Add New Category' selected. Showing input field.");
            }
        });

        // ✅ Add new category - CRITICAL FIX APPLIED HERE
        btnSaveCategory.setOnClickListener(v -> {
            String newCat = etNewCategory.getText().toString().trim();
            if (!newCat.isEmpty()) {
                Log.d(TAG, "Dialog: Saving new category: " + newCat);

                // 1. Add category (ASYNC WRITE to DB, triggers LiveData update)
                viewModel.addCategory(newCat);
                showCustomToast("New category added!");

                // 2. CRITICAL FIX: Wait for LiveData confirmation and perform UI switch/text set
                // This ensures the local lists/map are updated before setting the text.
                onCategoryAdded(newCat, adapter, categoriesList, categoryNameToIdMap,
                        actCategory, llAddCategory, etNewCategory);

            } else {
                showCustomToast("Category cannot be empty!");
                Log.w(TAG, "Dialog: Attempted to save empty category.");
            }
        });

        btnBackCategory.setOnClickListener(v -> {
            llAddCategory.setVisibility(View.GONE);
            actCategory.setVisibility(View.VISIBLE);
            Log.d(TAG, "Dialog: Cancel Add Category button clicked.");
        });

        // --- Date picker setup ---
        btnPickDateTime.setOnClickListener(v -> {
            Log.d(TAG, "Dialog: Date/Time picker clicked.");
            final Calendar calendar = Calendar.getInstance();
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

            if (!tvDateTime.getText().toString().isEmpty()) {
                try {
                    calendar.setTime(format.parse(tvDateTime.getText().toString()));
                } catch (Exception e) {
                    Log.e(TAG, "Dialog: Failed to parse existing date time string.", e);
                }
            }

            DatePickerDialog dp = new DatePickerDialog(
                    getContext(),
                    (view, y, m, d) -> {
                        calendar.set(y, m, d);
                        TimePickerDialog tp = new TimePickerDialog(
                                getContext(),
                                (timeView, h, min) -> {
                                    calendar.set(Calendar.HOUR_OF_DAY, h);
                                    calendar.set(Calendar.MINUTE, min);
                                    String selectedDateTime = format.format(calendar.getTime());
                                    tvDateTime.setText(selectedDateTime);
                                    Log.d(TAG, "Dialog: Selected date/time: " + selectedDateTime);
                                },
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                true
                        );
                        tp.show();
                        tp.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(Color.parseColor("#00BFA5"));
                        tp.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#FF5252"));
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            dp.show();
            dp.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(Color.parseColor("#00BFA5"));
            dp.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#FF5252"));
        });

        // --- Preload existing transaction data if editing ---
        if (transactionToEdit != null) {
            String categoryName = viewModel.getCategoryMap().get(transactionToEdit.getCategoryId());
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

            etAmount.setText(String.valueOf(transactionToEdit.getAmount()));
            etDescription.setText(transactionToEdit.getDescription());
            actCategory.setText(categoryName);
            tvDateTime.setText(format.format(new Date(transactionToEdit.getDateTime())));

            int radioId = "Income".equals(transactionToEdit.getType()) ? R.id.rbIncome : R.id.rbExpense;
            rgType.check(radioId);
            Log.d(TAG, "Dialog: Preloaded existing data for Edit mode. Category: " + categoryName + ", Type ID: " + radioId);
        }

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(popupView)
                .create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        Log.d(TAG, "Dialog: Add/Edit Transaction dialog shown.");

        // --- Save Transaction ---
        btnSave.setOnClickListener(v -> {
            Log.d(TAG, "Dialog: Save Transaction button clicked.");
            String amountText = etAmount.getText().toString().trim();
            String desc = etDescription.getText().toString().trim();
            String catName = actCategory.getText().toString().trim();
            String dateText = tvDateTime.getText().toString().trim();
            int checkedTypeId = rgType.getCheckedRadioButtonId();
            String type = checkedTypeId == R.id.rbIncome ? "Income" : "Expense";

            // 🔴 Validation Checks
            if (amountText.isEmpty() || catName.isEmpty() || dateText.isEmpty() || desc.isEmpty()) {
                showCustomToast("Fill all fields!");
                Log.w(TAG, "Validation Failed: Empty fields detected.");
                return;
            }

            if (checkedTypeId == -1) {
                showCustomToast("Please select transaction type (Income or Expense)!");
                Log.w(TAG, "Validation Failed: No transaction type selected.");
                return;
            }

            if (!categoryNameToIdMap.containsKey(catName)) {
                if ("Add New Category".equals(catName)) {
                    showCustomToast("Please select a valid category or complete adding a new one!");
                } else {
                    showCustomToast("Invalid category selected! Ensure it's from the list.");
                }
                Log.w(TAG, "Validation Failed: Invalid category name: " + catName);
                return;
            }

            int categoryId = categoryNameToIdMap.get(catName);
            Log.d(TAG, "Validation Passed. Category Name: " + catName + ", ID: " + categoryId);

            try {
                double amount = Double.parseDouble(amountText);
                long dateMillis = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .parse(dateText).getTime();

                if (transactionToEdit != null) {
                    // Check for changes in Edit mode
                    boolean noChange =
                            Double.compare(transactionToEdit.getAmount(), amount) == 0 &&
                                    Objects.equals(transactionToEdit.getDescription(), desc) &&
                                    transactionToEdit.getCategoryId() == categoryId &&
                                    transactionToEdit.getDateTime() == dateMillis &&
                                    Objects.equals(transactionToEdit.getType(), type);

                    if (noChange) {
                        showCustomToast("No changes detected!");
                        dialog.dismiss();
                        Log.i(TAG, "Edit transaction: No changes detected. Dismissing dialog.");
                        return;
                    }

                    // Perform update
                    transactionToEdit.setAmount(amount);
                    transactionToEdit.setDescription(desc);
                    transactionToEdit.setCategoryId(categoryId);
                    transactionToEdit.setDateTime(dateMillis);
                    transactionToEdit.setType(type);

                    viewModel.updateTransaction(transactionToEdit);
                    showCustomToast("Transaction updated successfully!");
                    Log.i(TAG, "Edit transaction: Update initiated for ID: " + transactionToEdit.getId());
                } else {
                    // Perform save
                    viewModel.saveTransaction(amount, type, categoryId, dateMillis, desc, dialog::dismiss);
                    showCustomToast("New transaction added!");
                    Log.i(TAG, "New transaction: Save initiated.");
                }

                dialog.dismiss();
            } catch (Exception e) {
                showCustomToast("Invalid amount or date format!");
                Log.e(TAG, "Save Failed: Parsing error (Amount/Date).", e);
            }
        });
    }

    // --- NEW METHOD: Waits for LiveData update before completing category add UI switch ---
    private void onCategoryAdded(String newCat, ArrayAdapter<String> adapter,
                                 List<String> categoriesList,
                                 Map<String, Integer> categoryNameToIdMap,
                                 AutoCompleteTextView actCategory,
                                 LinearLayout llAddCategory,
                                 EditText etNewCategory) {

        Log.d(TAG, "onCategoryAdded: Waiting for LiveData confirmation for '" + newCat + "'.");

        // Attach a one-time observer that waits for the LiveData to reflect the newly added category.
        Observer<Map<Integer, String>> oneTimeObserver = new Observer<Map<Integer, String>>() {
            @Override
            public void onChanged(Map<Integer, String> map) {
                Log.d(TAG, "onCategoryAdded Observer: LiveData changed. Size: " + (map != null ? map.size() : 0));

                if (map != null && map.containsValue(newCat)) {

                    // 1. Update local lists and map (Essential for subsequent validation/selection)
                    categoriesList.clear();
                    categoryNameToIdMap.clear();
                    categoriesList.add("Add New Category");

                    for (Map.Entry<Integer, String> entry : map.entrySet()) {
                        categoriesList.add(entry.getValue());
                        categoryNameToIdMap.put(entry.getValue(), entry.getKey());
                    }
                    adapter.notifyDataSetChanged();

                    Log.i(TAG, "onCategoryAdded Observer: Category '" + newCat + "' confirmed. Performing UI switch.");

                    // 2. Perform the UI switch and set the text on the main thread
                    actCategory.post(() -> {
                        actCategory.setText(newCat);
                        actCategory.setVisibility(View.VISIBLE);
                        llAddCategory.setVisibility(View.GONE);
                        actCategory.clearFocus();
                        etNewCategory.setText(""); // Clear the input field
                    });

                    // 3. CRUCIAL: Remove the observer after confirmation to avoid unnecessary future calls
                    viewModel.getCategoriesLive().removeObserver(this);
                } else if (map != null) {
                    Log.d(TAG, "onCategoryAdded Observer: Category not yet present. Waiting...");
                }
            }
        };

        // Attach the observer. The viewModel.addCategory() call should soon trigger the update.
        viewModel.getCategoriesLive().observe(getViewLifecycleOwner(), oneTimeObserver);
        // Also call fetch to ensure we don't miss the initial value or a quick update.
        viewModel.fetchLatestCategoryMap();
    }


    // 🔹 Helper: Refresh category list when focused
    public void refreshCategories(ArrayAdapter<String> adapter,
                                  List<String> categoriesList,
                                  Map<String, Integer> categoryNameToIdMap,
                                  AutoCompleteTextView actCategory) {

        Log.d(TAG, "refreshCategories: Triggering category fetch from ViewModel.");

        // 1. Force the ViewModel to fetch/reload the latest categories from its source (e.g., database)
        viewModel.fetchLatestCategoryMap();

        // 2. Observer to receive the *single* update after the fetch is complete.
        Observer<Map<Integer, String>> categoryObserver = new Observer<Map<Integer, String>>() {
            @Override
            public void onChanged(Map<Integer, String> map) {
                Log.d(TAG, "refreshCategories: LiveData onChanged triggered.");

                if (map == null) {
                    Log.w(TAG, "refreshCategories: Category map is null. Removing observer.");
                    viewModel.getCategoriesLive().removeObserver(this);
                    return;
                }

                // --- Update the local lists and map ---
                categoriesList.clear();
                categoryNameToIdMap.clear();

                // Add the placeholder
                categoriesList.add("Add New Category");

                // Populate with fresh data
                for (Map.Entry<Integer, String> entry : map.entrySet()) {
                    categoriesList.add(entry.getValue());
                    categoryNameToIdMap.put(entry.getValue(), entry.getKey());
                }
                Log.d(TAG, "refreshCategories: Populated with " + map.size() + " categories. Total list size: " + categoriesList.size());

                // --- Notify and show the dropdown ---
                adapter.notifyDataSetChanged();

                if (actCategory.getVisibility() == View.VISIBLE) {
                    actCategory.post(actCategory::showDropDown);
                }

                // CRUCIAL: Remove the observer after the update to prevent multiple observations
                viewModel.getCategoriesLive().removeObserver(this);
                Log.d(TAG, "refreshCategories: Observer removed successfully.");
            }
        };

        // Attach the observer
        viewModel.getCategoriesLive().observe(getViewLifecycleOwner(), categoryObserver);
        Log.d(TAG, "refreshCategories: New observer attached.");
    }

    private void showFilterMenu(String type) {
        Log.d(TAG, "showFilterMenu: Showing filter menu for type: " + type);
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(getContext(),
                type.equals("Income") ? binding.buttonFilterIncome : binding.buttonFilterExpenses);
        popup.getMenu().add("Sort by Date (Newest)");
        popup.getMenu().add("Sort by Date (Oldest)");
        popup.getMenu().add("Sort by Amount (High → Low)");
        popup.getMenu().add("Sort by Amount (Low → High)");
        popup.getMenu().add("Filter by Category");

        popup.setOnMenuItemClickListener(item -> {
            TextView filterTextView = type.equals("Income") ? binding.textFilterIncome : binding.textFilterExpenses;
            String selectedTitle = item.getTitle().toString();
            Log.d(TAG, "Filter menu item selected: " + selectedTitle + " for " + type);

            switch (selectedTitle) {
                case "Sort by Date (Newest)":
                    viewModel.sortTransactions(type, "date_desc");
                    filterTextView.setText("Date ↓");
                    break;
                case "Sort by Date (Oldest)":
                    viewModel.sortTransactions(type, "date_asc");
                    filterTextView.setText("Date ↑");
                    break;
                case "Sort by Amount (High → Low)":
                    viewModel.sortTransactions(type, "amount_desc");
                    filterTextView.setText("Amount ↓");
                    break;
                case "Sort by Amount (Low → High)":
                    viewModel.sortTransactions(type, "amount_asc");
                    filterTextView.setText("Amount ↑");
                    break;
                case "Filter by Category":
                    showCategoryFilterDialog(type);
                    break;
            }
            return true;
        });
        popup.show();
    }

    private void showCategoryFilterDialog(String type) {
        Log.d(TAG, "showCategoryFilterDialog: Preparing category filter for type: " + type);
        Map<Integer, String> categoryMap = viewModel.getCategoryMap();
        if (categoryMap == null || categoryMap.isEmpty()) {
            showCustomToast("No categories available to filter!");
            Log.w(TAG, "showCategoryFilterDialog: No categories found.");
            return;
        }

        List<String> options = new ArrayList<>();
        options.add("Show All");
        List<Integer> categoryIdsInOrder = new ArrayList<>();

        for (Map.Entry<Integer, String> entry : categoryMap.entrySet()) {
            options.add(entry.getValue());
            categoryIdsInOrder.add(entry.getKey());
        }
        Log.d(TAG, "showCategoryFilterDialog: Filter options count: " + options.size());

        TextView filterTextView = type.equals("Income") ? binding.textFilterIncome : binding.textFilterExpenses;

        Runnable onNoResultsAction = () -> {
            showCustomToast("No transactions found for this category.");
            Log.i(TAG, "showCategoryFilterDialog: No results found toast shown.");
        };

        new AlertDialog.Builder(getContext())
                .setTitle("Choose Category")
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    String selectedName = options.get(which);
                    Log.d(TAG, "Category Filter Dialog selected index: " + which + ", Name: " + selectedName);

                    if (which == 0) {
                        viewModel.filterByCategory(type, null,
                                () -> filterTextView.setText("Filter"),
                                null);
                        Log.i(TAG, "Category Filter: Showing All transactions for " + type);
                    } else {
                        int selectedCategoryId = categoryIdsInOrder.get(which - 1);

                        viewModel.filterByCategory(type, selectedCategoryId,
                                () -> filterTextView.setText(selectedName),
                                onNoResultsAction);
                        Log.i(TAG, "Category Filter: Filtering by ID " + selectedCategoryId + " (" + selectedName + ") for " + type);
                    }
                }).show();
    }

    private void updateTransactionVisibility() {
        List<Transaction> incomeList = viewModel.getIncomeTransactions().getValue();
        List<Transaction> expenseList = viewModel.getExpenseTransactions().getValue();

        boolean hasIncome = incomeList != null && !incomeList.isEmpty();
        boolean hasExpense = expenseList != null && !expenseList.isEmpty();
        boolean empty = !hasIncome && !hasExpense;

        Log.d(TAG, "updateTransactionVisibility: Income? " + hasIncome + ", Expense? " + hasExpense + ", Empty? " + empty);

        binding.textIncomeLabel.setVisibility(hasIncome ? View.VISIBLE : View.GONE);
        binding.recyclerIncome.setVisibility(hasIncome ? View.VISIBLE : View.GONE);
        binding.buttonFilterIncome.setVisibility(hasIncome ? View.VISIBLE : View.GONE);

        binding.textExpensesLabel.setVisibility(hasExpense ? View.VISIBLE : View.GONE);
        binding.recyclerExpenses.setVisibility(hasExpense ? View.VISIBLE : View.GONE);
        binding.buttonFilterExpenses.setVisibility(hasExpense ? View.VISIBLE : View.GONE);

        binding.imageNoTransactions.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.textNoTransactions.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void showCustomToast(String message) {
        Log.d(TAG, "showCustomToast: Displaying message: " + message);
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_message, null);

        TextView toastMessage = layout.findViewById(R.id.toast_message);
        ImageView close = layout.findViewById(R.id.toast_close);
        ProgressBar progressBar = layout.findViewById(R.id.toast_progress);

        toastMessage.setText(message);
        progressBar.setProgress(100);

        AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(layout).create();
        close.setOnClickListener(v -> {
            Log.d(TAG, "Custom Toast closed by user.");
            dialog.dismiss();
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0f);

            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = android.view.Gravity.TOP;
            params.y = 50;
            dialog.getWindow().setAttributes(params);
        }

        dialog.show();

        // 🔹 Countdown timer for auto-dismiss with progress bar
        new CountDownTimer(3000, 50) {
            public void onTick(long millisUntilFinished) {
                int progress = (int) Math.max(0, Math.round((millisUntilFinished / 3000.0) * 100));
                progressBar.setProgress(progress);
            }

            public void onFinish() {
                if (dialog.isShowing()) {
                    Log.d(TAG, "Custom Toast auto-dismissed.");
                    dialog.dismiss();
                }
            }
        }.start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView: Binding cleared.");
        binding = null;
    }
}