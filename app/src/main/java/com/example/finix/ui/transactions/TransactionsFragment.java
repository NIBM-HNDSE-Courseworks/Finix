package com.example.finix.ui.transactions;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.finix.R;
import com.example.finix.data.Transaction;
import com.example.finix.databinding.FragmentTransactionsBinding;
import java.text.SimpleDateFormat;
import java.util.*;

public class TransactionsFragment extends Fragment {

    private FragmentTransactionsBinding binding;
    private TransactionsViewModel viewModel;
    private TransactionAdapter incomeAdapter, expenseAdapter;

    @Override
    public void onResume() {
        super.onResume();
        // Force a reload just in case the LiveData update was missed
        // while the fragment was paused/not in the foreground.
        viewModel.loadAllTransactions();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

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

        // 🔹 Add spacing between items
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

        // 🔹 Observe transactions
        viewModel.getIncomeTransactions().observe(getViewLifecycleOwner(), list -> {
            incomeAdapter.setTransactions(list);
            updateTransactionVisibility();
        });
        viewModel.getExpenseTransactions().observe(getViewLifecycleOwner(), list -> {
            expenseAdapter.setTransactions(list);
            updateTransactionVisibility();
        });

        viewModel.getMessageEvent().observe(getViewLifecycleOwner(), message -> {
            if (message != null) {
                showCustomToast(message);
                // Optional: You might want to clear the event after it's been consumed
                // (requires a method in the ViewModel or using a SingleEvent LiveData implementation)
            }
        });

        // 🔹 Keep category map updated in adapters
        viewModel.getCategoriesLive().observe(getViewLifecycleOwner(), map -> {
            if (map != null) {
                incomeAdapter.setCategoryMap(map);
                expenseAdapter.setCategoryMap(map);
                incomeAdapter.notifyDataSetChanged();
                expenseAdapter.notifyDataSetChanged();
            }
        });

        // 🔹 Add transaction
        binding.buttonAddTransaction.setOnClickListener(v -> showAddTransactionDialog(null));

        // 🔹 Filters
        binding.buttonFilterIncome.setOnClickListener(v -> showFilterMenu("Income"));
        binding.buttonFilterExpenses.setOnClickListener(v -> showFilterMenu("Expense"));

        // 🔹 Edit & Delete buttons inside adapter
        incomeAdapter.setListener(new TransactionAdapter.OnTransactionActionListener() {
            @Override
            public void onEdit(Transaction t) {
                showAddTransactionDialog(t);
            }

            @Override
            public void onDelete(Transaction t) {
                AlertDialog dialog = new AlertDialog.Builder(getContext())
                        .setTitle("Delete Transaction")
                        .setMessage("Are you sure you want to delete this transaction?")
                        .setPositiveButton("Yes", (d, w) -> viewModel.deleteTransaction(t))
                        .setNegativeButton("No", null)
                        .create();

                dialog.setOnShowListener(d -> {
                    // 🔹 Positive button (Yes)
                    Button btnYes = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    btnYes.setTextColor(Color.parseColor("#00BFA5")); // green-ish
                    btnYes.setBackground(new RippleDrawable(
                            ColorStateList.valueOf(Color.parseColor("#3300BFA5")),
                            null, null
                    ));

                    // 🔹 Negative button (No)
                    Button btnNo = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                    btnNo.setTextColor(Color.parseColor("#FF5252")); // red-ish
                    btnNo.setBackground(new RippleDrawable(
                            ColorStateList.valueOf(Color.parseColor("#33FF5252")),
                            null, null
                    ));
                });

                dialog.show();
            }
        });

        expenseAdapter.setListener(new TransactionAdapter.OnTransactionActionListener() {
            @Override
            public void onEdit(Transaction t) {
                showAddTransactionDialog(t);
            }

            @Override
            public void onDelete(Transaction t) {
                AlertDialog dialog = new AlertDialog.Builder(getContext())
                        .setTitle("Delete Transaction")
                        .setMessage("Are you sure you want to delete this transaction?")
                        .setPositiveButton("Yes", (d, w) -> viewModel.deleteTransaction(t))
                        .setNegativeButton("No", null)
                        .create();

                dialog.setOnShowListener(d -> {
                    // 🔹 Positive button (Yes)
                    Button btnYes = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    btnYes.setTextColor(Color.parseColor("#00BFA5")); // green-ish
                    btnYes.setBackground(new RippleDrawable(
                            ColorStateList.valueOf(Color.parseColor("#3300BFA5")),
                            null, null
                    ));

                    // 🔹 Negative button (No)
                    Button btnNo = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                    btnNo.setTextColor(Color.parseColor("#FF5252")); // red-ish
                    btnNo.setBackground(new RippleDrawable(
                            ColorStateList.valueOf(Color.parseColor("#33FF5252")),
                            null, null
                    ));
                });

                dialog.show();
            }
        });

        return root;
    }

    // 🔹 Popup for Add/Edit Transaction
    private void showAddTransactionDialog(Transaction transactionToEdit) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View popupView = inflater.inflate(R.layout.add_edit_transaction_popup, null);

        // --- Title ---
        TextView tvTitle = popupView.findViewById(R.id.popupTitle);
        if (transactionToEdit != null) {
            tvTitle.setText("Edit Transaction");
        } else {
            tvTitle.setText("Add Transaction");
        }

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

        // --- Category setup (FIX APPLIED HERE) ---
        List<String> categoriesList = new ArrayList<>();
        Map<String, Integer> categoryNameToIdMap = new HashMap<>();

        // 🟢 FIX: Pre-populate the map using the latest data from the ViewModel
        Map<Integer, String> currentCategories = viewModel.getCategoryMap();

        if (currentCategories != null) {
            for (Map.Entry<Integer, String> entry : currentCategories.entrySet()) {
                categoriesList.add(entry.getValue());
                categoryNameToIdMap.put(entry.getValue(), entry.getKey());
            }
        }

        if (!categoriesList.contains("Add New Category")) {
            categoriesList.add("Add New Category");
        }
        // 🟢 END OF FIX

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_dropdown_item_1line, categoriesList);
        actCategory.setAdapter(adapter);
        actCategory.setThreshold(0);

        // ✅ Load categories when user focuses on the field (retains functionality for new categories)
        actCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                refreshCategories(adapter, categoriesList, categoryNameToIdMap, actCategory);
            }
        });

        actCategory.setOnClickListener(v -> {
            if (!actCategory.isPopupShowing()) {
                refreshCategories(adapter, categoriesList, categoryNameToIdMap, actCategory);
            }
        });

        actCategory.setOnItemClickListener((parent, view, position, id) -> {
            String selected = adapter.getItem(position);
            if ("Add New Category".equals(selected)) {
                llAddCategory.setVisibility(View.VISIBLE);
                actCategory.setVisibility(View.GONE);
            }
        });

        // ✅ Add new category
        btnSaveCategory.setOnClickListener(v -> {
            String newCat = etNewCategory.getText().toString().trim();
            if (!newCat.isEmpty()) {
                viewModel.addCategory(newCat);
                showCustomToast("New category added!");

                actCategory.postDelayed(() -> {
                    actCategory.setText(newCat);
                    actCategory.setVisibility(View.VISIBLE);
                    llAddCategory.setVisibility(View.GONE);
                    actCategory.clearFocus(); // ensures fresh reload next focus
                }, 200);

                etNewCategory.setText("");
            } else {
                showCustomToast("Category cannot be empty!");
            }
        });

        btnBackCategory.setOnClickListener(v -> {
            llAddCategory.setVisibility(View.GONE);
            actCategory.setVisibility(View.VISIBLE);
        });

        // --- Date picker setup ---
        btnPickDateTime.setOnClickListener(v -> {
            final Calendar calendar = Calendar.getInstance();

            if (!tvDateTime.getText().toString().isEmpty()) {
                try {
                    calendar.setTime(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            .parse(tvDateTime.getText().toString()));
                } catch (Exception ignored) {}
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
                                    tvDateTime.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                            .format(calendar.getTime()));
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
            etAmount.setText(String.valueOf(transactionToEdit.getAmount()));
            etDescription.setText(transactionToEdit.getDescription());
            actCategory.setText(categoryName);
            tvDateTime.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(new Date(transactionToEdit.getDateTime())));
            if ("Income".equals(transactionToEdit.getType())) rgType.check(R.id.rbIncome);
            else rgType.check(R.id.rbExpense);
        }

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(popupView)
                .create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // --- Save Transaction ---
        btnSave.setOnClickListener(v -> {
            String amountText = etAmount.getText().toString().trim();
            String desc = etDescription.getText().toString().trim();
            String catName = actCategory.getText().toString().trim();
            String dateText = tvDateTime.getText().toString().trim();
            String type = rgType.getCheckedRadioButtonId() == R.id.rbIncome ? "Income" : "Expense";

            if (amountText.isEmpty() || catName.isEmpty() || dateText.isEmpty()) {
                showCustomToast("Fill all fields!");
                return;
            }

            // The map is now populated, so this check works correctly even if the user didn't touch the category field
            if (!categoryNameToIdMap.containsKey(catName)) {
                showCustomToast("Invalid category selected!");
                return;
            }

            int categoryId = categoryNameToIdMap.get(catName);

            try {
                double amount = Double.parseDouble(amountText);
                long dateMillis = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .parse(dateText).getTime();

                if (transactionToEdit != null) {
                    boolean noChange =
                            Double.compare(transactionToEdit.getAmount(), amount) == 0 &&
                                    Objects.equals(transactionToEdit.getDescription(), desc) &&
                                    transactionToEdit.getCategoryId() == categoryId &&
                                    transactionToEdit.getDateTime() == dateMillis &&
                                    Objects.equals(transactionToEdit.getType(), type);

                    if (noChange) {
                        showCustomToast("No changes detected!");
                        dialog.dismiss(); // Dismiss the dialog here
                        return;
                    }

                    transactionToEdit.setAmount(amount);
                    transactionToEdit.setDescription(desc);
                    transactionToEdit.setCategoryId(categoryId);
                    transactionToEdit.setDateTime(dateMillis);
                    transactionToEdit.setType(type);

                    viewModel.updateTransaction(transactionToEdit);
                    showCustomToast("Transaction updated successfully!");
                } else {
                    viewModel.saveTransaction(amount, type, categoryId, dateMillis, desc, dialog::dismiss);
                    showCustomToast("New transaction added!");
                }

                dialog.dismiss();
            } catch (Exception e) {
                showCustomToast("Invalid amount or date format!");
            }
        });

        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    // 🔹 Helper: Refresh category list when focused
    public void refreshCategories(ArrayAdapter<String> adapter,
                                   List<String> categoriesList,
                                   Map<String, Integer> categoryNameToIdMap,
                                   AutoCompleteTextView actCategory) {

        // 🔹 Ask ViewModel to reload categories in background
        viewModel.fetchLatestCategoryMap();

        // 🔹 Observe updated LiveData
        viewModel.getCategoriesLive().observe(getViewLifecycleOwner(), map -> {
            if (map == null) return;

            categoriesList.clear();
            categoryNameToIdMap.clear();

            for (Map.Entry<Integer, String> entry : map.entrySet()) {
                categoriesList.add(entry.getValue());
                categoryNameToIdMap.put(entry.getValue(), entry.getKey());
            }

            if (!categoriesList.contains("Add New Category")) {
                categoriesList.add("Add New Category");
            }

            adapter.notifyDataSetChanged();
            actCategory.post(actCategory::showDropDown);
        });
    }

    private void showFilterMenu(String type) {
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
        Map<Integer, String> categoryMap = viewModel.getCategoryMap(); // 🔹 Map: ID → Name
        if (categoryMap == null || categoryMap.isEmpty()) {
            showCustomToast("No categories available to filter!");
            return;
        }

        List<String> options = new ArrayList<>();
        options.add("Show All");
        // Use a separate list for IDs, aligning with options index
        List<Integer> categoryIdsInOrder = new ArrayList<>();

        for (Map.Entry<Integer, String> entry : categoryMap.entrySet()) {
            options.add(entry.getValue());
            categoryIdsInOrder.add(entry.getKey()); // Add ID in the same order as name is added to options
        }

        TextView filterTextView = type.equals("Income") ? binding.textFilterIncome : binding.textFilterExpenses;

        // 🚨 NEW: Create the runnable for "no results" toast
        Runnable onNoResultsAction = () -> {
            showCustomToast("No transactions found for this category.");
        };

        new AlertDialog.Builder(getContext())
                .setTitle("Choose Category")
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        // 🟢 Show all
                        viewModel.filterByCategory(type, null,
                                () -> filterTextView.setText("Filter"),
                                null); // No 'onNoResults' needed for 'Show All'
                    } else {
                        // 🔹 Filter by selected category ID
                        // The ID is at index 'which - 1' in the categoryIdsInOrder list
                        int selectedCategoryId = categoryIdsInOrder.get(which - 1);
                        String selectedName = options.get(which); // Get name from options list

                        viewModel.filterByCategory(type, selectedCategoryId,
                                () -> filterTextView.setText(selectedName),
                                onNoResultsAction); // 🚨 PASS THE NEW ACTION HERE
                    }
                }).show();
    }

    private void updateTransactionVisibility() {
        List<Transaction> incomeList = viewModel.getIncomeTransactions().getValue();
        List<Transaction> expenseList = viewModel.getExpenseTransactions().getValue();

        boolean hasIncome = incomeList != null && !incomeList.isEmpty();
        boolean hasExpense = expenseList != null && !expenseList.isEmpty();

        binding.textIncomeLabel.setVisibility(hasIncome ? View.VISIBLE : View.GONE);
        binding.recyclerIncome.setVisibility(hasIncome ? View.VISIBLE : View.GONE);
        binding.buttonFilterIncome.setVisibility(hasIncome ? View.VISIBLE : View.GONE);

        binding.textExpensesLabel.setVisibility(hasExpense ? View.VISIBLE : View.GONE);
        binding.recyclerExpenses.setVisibility(hasExpense ? View.VISIBLE : View.GONE);
        binding.buttonFilterExpenses.setVisibility(hasExpense ? View.VISIBLE : View.GONE);

        boolean empty = !hasIncome && !hasExpense;
        binding.imageNoTransactions.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.textNoTransactions.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void showCustomToast(String message) { // Method to show a custom toast-like message popup
        LayoutInflater inflater = getLayoutInflater(); // Get layout inflater for inflating XML
        View layout = inflater.inflate(R.layout.custom_message, null); // Inflate custom toast layout

        // 🔹 Initialize views inside custom layout
        TextView toastMessage = layout.findViewById(R.id.toast_message); // Text field for message
        ImageView close = layout.findViewById(R.id.toast_close); // Close (X) button
        ProgressBar progressBar = layout.findViewById(R.id.toast_progress); // Progress bar for auto-dismiss timer

        toastMessage.setText(message); // Set message text
        progressBar.setProgress(100); // Start with full progress (100%)

        // 🔹 Create dialog to show custom toast
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(layout).create();
        close.setOnClickListener(v -> dialog.dismiss()); // Close button → dismiss toast

        if (dialog.getWindow() != null) { // Customize dialog window
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); // Transparent background
            dialog.getWindow().setDimAmount(0f); // No dim background

            WindowManager.LayoutParams params = dialog.getWindow().getAttributes(); // Get window attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT; // Full width
            params.height = WindowManager.LayoutParams.WRAP_CONTENT; // Wrap height
            params.gravity = android.view.Gravity.TOP; // Show at top of screen
            params.y = 50; // Add top margin (distance from status bar)
            dialog.getWindow().setAttributes(params); // Apply attributes
        }

        dialog.show(); // Show the custom toast dialog

        // 🔹 Countdown timer for auto-dismiss with progress bar
        new CountDownTimer(3000, 50) { // 3 seconds total, tick every 50ms
            public void onTick(long millisUntilFinished) { // Update progress on each tick
                int progress = (int) Math.max(0, Math.round((millisUntilFinished / 3000.0) * 100));
                // Convert remaining time to percentage
                progressBar.setProgress(progress); // Update progress bar
            }

            public void onFinish() { // Called after 3 seconds
                if (dialog.isShowing()) dialog.dismiss(); // Auto dismiss toast if still showing
            }
        }.start(); // Start countdown
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}