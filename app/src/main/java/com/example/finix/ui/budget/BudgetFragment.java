package com.example.finix.ui.budget;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView; // <-- Ensure this is imported
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView; // <-- Ensure this is imported
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView; // <-- Need to access RecyclerView outside of binding

import com.example.finix.R;
import com.example.finix.data.Budget;
import com.example.finix.data.Category;
import com.example.finix.data.CategoryDAO;
import com.example.finix.data.FinixDatabase;
import com.example.finix.data.SynchronizationLog;
import com.example.finix.data.SynchronizationLogDAO;
import com.example.finix.data.Transaction;
import com.example.finix.databinding.FragmentBudgetBinding;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BudgetFragment extends Fragment {

    private FragmentBudgetBinding binding;
    private BudgetViewModel budgetViewModel;
    private BudgetAdapter adapter;

    // 💡 NEW: Placeholder views for empty state
    private ImageView imageNoBudgets;
    private TextView textNoBudgets;
    private RecyclerView recyclerBudgets;
    // ------------------------------------

    private long startDateMillis = 0;
    private long endDateMillis = 0;

    public interface OnBudgetActionListener {
        void onEdit(Budget budget);
        void onDelete(Budget budget);
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentBudgetBinding.inflate(inflater, container, false);
        budgetViewModel = new ViewModelProvider(this).get(BudgetViewModel.class);

        // 💡 NEW: Initialize RecyclerView and Placeholder views
        recyclerBudgets = binding.recyclerBudgets;
        imageNoBudgets = binding.getRoot().findViewById(R.id.imageNoBudgets);
        textNoBudgets = binding.getRoot().findViewById(R.id.textNoBudgets);
        // ------------------------------------

        // --- Adapter initialized with listener for edit/delete ---
        adapter = new BudgetAdapter(requireContext(), new BudgetAdapter.OnBudgetActionListener() {
            @Override
            public void onEdit(Budget budget) {
                showAddBudgetDialog(budget); // Edit
            }

            @Override
            public void onDelete(Budget budget) {
                showDeleteConfirmationDialog(budget); //Delete
            }
        });

        recyclerBudgets.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerBudgets.setAdapter(adapter);

        loadBudgets();

        binding.btnAddBudget.setOnClickListener(v -> showAddBudgetDialog(null));

        return binding.getRoot();
    }

    private void loadBudgets() {
        new Thread(() -> {
            List<Budget> budgets = budgetViewModel.getAllBudgets();
            List<Transaction> transactions = FinixDatabase.getDatabase(requireContext()).transactionDao().getAllTransactions();
            List<Category> categories = FinixDatabase.getDatabase(requireContext()).categoryDao().getAllCategories();

            requireActivity().runOnUiThread(() -> {
                // Check if any budgets were loaded
                boolean hasBudgets = budgets != null && !budgets.isEmpty();

                // Get a reference to the Spinner
                View spinner = binding.spinnerMonthFilter;

                if (hasBudgets) {
                    // 1. Show the budget list and the filter
                    recyclerBudgets.setVisibility(View.VISIBLE);
                    spinner.setVisibility(View.VISIBLE); // SHOW the filter

                    // 2. Hide the empty state indicators
                    imageNoBudgets.setVisibility(View.GONE);
                    textNoBudgets.setVisibility(View.GONE);

                    // 3. Load data into the adapter and populate the filter options
                    adapter.setData(budgets, transactions, categories);
                    loadMonthFilter(budgets);
                } else {
                    // 1. Hide the budget list, but make the filter INVISIBLE (keeps its space)
                    recyclerBudgets.setVisibility(View.GONE);
                    spinner.setVisibility(View.INVISIBLE); // 💡 FIX: Use INVISIBLE to prevent the button from moving left

                    // 2. Show the empty state indicators
                    imageNoBudgets.setVisibility(View.VISIBLE);
                    textNoBudgets.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    private void showAddBudgetDialog(Budget budgetToEdit) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_budget, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(dialogView).create();

        if (dialog.getWindow() != null) {
            // CRITICAL FIX 1: Force Full Width and Full Height
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);

            // CRITICAL FIX 2: Ensure dialog resizes when keyboard appears
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

            // CRITICAL FIX 3: Remove default AlertDialog padding/insets for true full screen
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle); // Added for title change
        AutoCompleteTextView etCategory = dialogView.findViewById(R.id.actCategory);
        EditText etAmount = dialogView.findViewById(R.id.etAmount);
        Button btnSave = dialogView.findViewById(R.id.btnSaveBudget);

        LinearLayout llAddNewCategory = dialogView.findViewById(R.id.llAddNewCategory);
        EditText etNewCategory = dialogView.findViewById(R.id.etNewCategory);
        Button btnSaveCategory = dialogView.findViewById(R.id.btnSaveCategory);
        Button btnCancelCategory = dialogView.findViewById(R.id.btnCancelCategory);

        TextView tvStartDate = dialogView.findViewById(R.id.StartDate);
        TextView tvEndDate = dialogView.findViewById(R.id.EndDate);
        ImageButton btnPickDate1 = dialogView.findViewById(R.id.btnPickDate1);
        ImageButton btnPickDate2 = dialogView.findViewById(R.id.btnPickDate2);

        // --- Variables to store original values for edit check ---
        final boolean isEditing = (budgetToEdit != null);
        final long originalStartDate = isEditing ? budgetToEdit.getStartDate() : 0;
        final long originalEndDate = isEditing ? budgetToEdit.getEndDate() : 0;
        final double originalAmount = isEditing ? budgetToEdit.getBudgetedAmount() : 0.0;
        // Category ID requires an async lookup, so we use a holder
        final int[] originalCategoryId = {isEditing ? budgetToEdit.getCategoryId() : 0};
        final int originalBudgetId = isEditing ? budgetToEdit.getLocalId() : 0; // Needed for exclusion check in Edit Mode

        // --- UI Setup for Edit Mode ---
        if (isEditing) {
            tvDialogTitle.setText("Edit Budget");
            btnSave.setText("Update Budget");
        } else {
            tvDialogTitle.setText("Add New Budget");
            btnSave.setText("Save");
        }


        // Load categories for AutoComplete
        new Thread(() -> {
            CategoryDAO categoryDao = FinixDatabase.getDatabase(requireContext()).categoryDao();
            List<Category> categoryList = categoryDao.getAllCategories();

            // 1. Initialize the list with the special option first (Fix for category order)
            List<String> categoryNames = new ArrayList<>();
            categoryNames.add("+ Add New Category");

            // 2. Add all fetched category names after the special option
            for (Category c : categoryList) {
                categoryNames.add(c.getName());
            }

            requireActivity().runOnUiThread(() -> {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        categoryNames
                );
                etCategory.setAdapter(adapter);

                // Show dropdown on click
                etCategory.setOnClickListener(v -> etCategory.showDropDown());

                // Handle selection
                etCategory.setOnItemClickListener((parent, view, position, id) -> {
                    String selected = categoryNames.get(position);
                    if (selected.equals("+ Add New Category")) {
                        etCategory.setVisibility(View.GONE);
                        llAddNewCategory.setVisibility(View.VISIBLE);

                        // ✅ Auto-focus & show keyboard
                        etNewCategory.requestFocus();
                        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.showSoftInput(etNewCategory, InputMethodManager.SHOW_IMPLICIT);
                    } else {
                        llAddNewCategory.setVisibility(View.GONE);
                    }
                });
            });
        }).start();

        // Date Pickers
        btnPickDate1.setOnClickListener(v -> showDatePicker(tvStartDate, true));
        btnPickDate2.setOnClickListener(v -> showDatePicker(tvEndDate, false));

        // --- Prefill if editing ---
        if (isEditing) { // <-- Use isEditing flag
            etAmount.setText(String.valueOf(budgetToEdit.getBudgetedAmount()));
            startDateMillis = originalStartDate; // Set initial state
            endDateMillis = originalEndDate;      // Set initial state
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            tvStartDate.setText(sdf.format(startDateMillis));
            tvEndDate.setText(sdf.format(endDateMillis));

            new Thread(() -> {
                List<Category> categories = FinixDatabase.getDatabase(requireContext()).categoryDao().getAllCategories();
                for (Category c : categories) {
                    if (c.getLocalId() == budgetToEdit.getCategoryId()) {
                        String catName = c.getName();
                        requireActivity().runOnUiThread(() -> etCategory.setText(catName));
                        // originalCategoryId[0] is already set, but confirm it's correct
                        originalCategoryId[0] = c.getLocalId();
                        break;
                    }
                }
            }).start();
        }

        // Save new category
        btnSaveCategory.setOnClickListener(v -> {
            String newCategoryName = etNewCategory.getText().toString().trim();
            if (newCategoryName.isEmpty()) {
                Toast.makeText(getContext(), "Please enter a category name", Toast.LENGTH_SHORT).show();
                return;
            }

            new Thread(() -> {
                try {
                    CategoryDAO categoryDao = FinixDatabase.getDatabase(requireContext()).categoryDao();
                    SynchronizationLogDAO syncDao = FinixDatabase.getDatabase(requireContext()).synchronizationLogDao();

                    // 1️⃣ Insert the new category
                    Category newCategory = new Category(newCategoryName);
                    long newRowId = categoryDao.insert(newCategory); // Get new category ID

                    // 2️⃣ Add to sync log
                    SynchronizationLog log = new SynchronizationLog(
                            "categories",
                            (int)newRowId,
                            System.currentTimeMillis(),
                            "PENDING"
                    );
                    syncDao.insert(log);

                    // 3️⃣ Fetch updated categories
                    List<Category> updated = categoryDao.getAllCategories();
                    List<String> updatedNames = new ArrayList<>();
                    updatedNames.add("+ Add New Category"); // Always first
                    for (Category c : updated) updatedNames.add(c.getName());

                    // 4️⃣ Handle editing scenario
                    if (isEditing) {
                        Category savedCat = categoryDao.getCategoryById((int)newRowId);
                        if (savedCat != null) {
                            originalCategoryId[0] = savedCat.getLocalId();
                        }
                    }

                    // 5️⃣ Update UI on main thread
                    requireActivity().runOnUiThread(() -> {
                        showCustomToast("Category Added");

                        llAddNewCategory.setVisibility(View.GONE);
                        etCategory.setVisibility(View.VISIBLE);
                        etCategory.setText(newCategoryName);

                        ArrayAdapter<String> newAdapter = new ArrayAdapter<>(
                                requireContext(),
                                android.R.layout.simple_dropdown_item_1line,
                                updatedNames
                        );
                        etCategory.setAdapter(newAdapter);
                    });

                } catch (Exception e) {
                    requireActivity().runOnUiThread(() ->
                            showCustomToast("Error adding category: " + e.getMessage())
                    );
                    e.printStackTrace();
                }
            }).start();
        });


        // Cancel adding category
        btnCancelCategory.setOnClickListener(v -> {
            llAddNewCategory.setVisibility(View.GONE);
            etCategory.setVisibility(View.VISIBLE);
        });


        // Save/Update Budget
        btnSave.setOnClickListener(v -> {
            String categoryName = etCategory.getText().toString().trim();
            String amountText = etAmount.getText().toString().trim();

            if (categoryName.isEmpty() || amountText.isEmpty() || startDateMillis == 0 || endDateMillis == 0) {
                showCustomToast("Fill all Fields");
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountText);
            } catch (NumberFormatException e) {
                showCustomToast("Invalid Amount: " + e.getMessage());
                return;
            }

            //Limit the budget amount to 999,999
            if (amount > 999999) {
                requireActivity().runOnUiThread(() ->
                        showCustomToast("Amount cannot exceed Rs 999,999"));
                return;
            }


            new Thread(() -> {
                try {
                    CategoryDAO categoryDao = FinixDatabase.getDatabase(requireContext()).categoryDao();
                    List<Category> allCategories = categoryDao.getAllCategories();

                    // Find existing category
                    Category matchingCategory = null;
                    for (Category c : allCategories) {
                        if (c.getName().equalsIgnoreCase(categoryName)) {
                            matchingCategory = c;
                            break;
                        }
                    }

                    int categoryId;
                    if (matchingCategory == null) {
                        // If category is brand new and not saved via the inline form, save it now.
                        Category newCat = new Category(categoryName);
                        categoryDao.insert(newCat);

                        // Fetch latest category
                        List<Category> updated = categoryDao.getAllCategories();
                        categoryId = updated.get(updated.size() - 1).getLocalId();
                    } else {
                        categoryId = matchingCategory.getLocalId();
                    }

                    // --- NEW VALIDATION: Check for existing budget in the same month/category ---

                    // Set up a SimpleDateFormat to compare only Month and Year
                    SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
                    String newBudgetDateString = monthYearFormat.format(new Date(startDateMillis));

                    List<Budget> existingBudgets = budgetViewModel.getAllBudgets();

                    for (Budget existingBudget : existingBudgets) {
                        // Skip the current budget being edited to allow changes to itself
                        if (isEditing && existingBudget.getLocalId() == originalBudgetId) {
                            continue;
                        }

                        // 1. Check if the categories match
                        if (existingBudget.getCategoryId() == categoryId) {

                            String existingBudgetDateString = monthYearFormat.format(new Date(existingBudget.getStartDate()));

                            // 2. Check if the month and year match
                            if (newBudgetDateString.equals(existingBudgetDateString)) {

                                // DUPLICATE BUDGET FOUND! Prevent insertion/update.
                                requireActivity().runOnUiThread(() -> {
                                    showCustomToast("A budget for '" + categoryName + "' already exists in " + newBudgetDateString);
                                });
                                return; // STOP execution of the thread
                            }
                        }
                    }
                    // ------------------ END NEW VALIDATION -------------------

                    // --- VALIDATION: Check for no changes during Edit mode ---
                    if (isEditing) {
                        boolean categoryChanged = (categoryId != originalCategoryId[0]);
                        boolean amountChanged = (Math.abs(amount - originalAmount) > 0.001); // Use delta for double comparison
                        boolean startDateChanged = (startDateMillis != originalStartDate);
                        boolean endDateChanged = (endDateMillis != originalEndDate);

                        if (!categoryChanged && !amountChanged && !startDateChanged && !endDateChanged) {
                            requireActivity().runOnUiThread(() -> {
                                // Do NOT dismiss dialog
                                showCustomToast("No changes detected.");
                            });
                            return; // Stop execution, keep dialog open
                        }
                    }

                    // 🔑 FIX: Initialize successMessage here so it's effectively final for the lambda
                    final String successMessage;
                    if (isEditing) {
                        successMessage = "Budget Updated Successfully";
                    } else {
                        successMessage = "Budget Added Successfully";
                    }

                    // --- Setup OnComplete Callback (The Fix) ---
                    Runnable onComplete = () -> {
                        requireActivity().runOnUiThread(() -> {
                            loadBudgets(); // <-- This runs ONLY after the DB operation finishes
                            dialog.dismiss();
                            showCustomToast(successMessage); // successMessage is now guaranteed to be initialized
                        });
                    };
                    // ------------------------------------------

                    // --- Proceed with Save/Update ---
                    if (isEditing) { // <-- Edit mode
                        budgetToEdit.setCategoryId(categoryId);
                        budgetToEdit.setBudgetedAmount(amount);
                        budgetToEdit.setStartDate(startDateMillis);
                        budgetToEdit.setEndDate(endDateMillis);
                        // 🔑 Pass the callback to the ViewModel
                        budgetViewModel.update(budgetToEdit, onComplete);
                    } else {
                        Budget newBudget = new Budget(categoryId, amount, startDateMillis, endDateMillis);
                        // 🔑 Pass the callback to the ViewModel
                        budgetViewModel.insert(newBudget, onComplete);
                    }


                } catch (Exception e) {
                    requireActivity().runOnUiThread(() ->
                            showCustomToast("Error Saving Budget: " + e.getMessage())
                    );
                    e.printStackTrace();
                }
            }).start();
        });

        dialog.show();
    }



    private void showDatePicker(TextView targetView, boolean isStart) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    // Ensure time is set to midnight (00:00:00) for accurate date comparisons
                    calendar.set(year, month, dayOfMonth, 0, 0, 0);
                    calendar.set(Calendar.MILLISECOND, 0);

                    long selectedDate = calendar.getTimeInMillis();
                    SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                    targetView.setText(sdf.format(calendar.getTime()));

                    if (isStart) startDateMillis = selectedDate;
                    else endDateMillis = selectedDate;
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.show();

        // --- ADDED: Color customization for buttons ---
        datePickerDialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setTextColor(Color.parseColor("#00BFA5")); // Teal color for OK button
        datePickerDialog.getButton(DialogInterface.BUTTON_NEGATIVE)
                .setTextColor(Color.parseColor("#FF5252")); // Red color for CANCEL button
        // ---------------------------------------------
    }

    /**
     * Replaces showCustomMessage with a custom toast-like AlertDialog at the bottom.
     * @param message The message to display.
     */
    private void showCustomToast(String message) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.custom_message, null);

        TextView text = layout.findViewById(R.id.toast_message);
        ImageView close = layout.findViewById(R.id.toast_close);
        ProgressBar progressBar = layout.findViewById(R.id.toast_progress);

        text.setText(message);
        progressBar.setProgress(100);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(layout)
                .create();

        close.setOnClickListener(v -> dialog.dismiss());

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0f);

            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;

            // Set gravity to BOTTOM
            params.gravity = android.view.Gravity.BOTTOM;

            // Set offset from bottom in pixels
            params.y = 50;

            dialog.getWindow().setAttributes(params);
        }

        dialog.show();

        new CountDownTimer(3000, 50) {
            public void onTick(long millisUntilFinished) {
                int progress = (int) ((millisUntilFinished / 3000.0) * 100);
                progressBar.setProgress(progress);
            }
            public void onFinish() {
                if (dialog.isShowing()) dialog.dismiss();
            }
        }.start();
    }

    // Inside BudgetFragment.java
    // Inside BudgetFragment.java
    private void showDeleteConfirmationDialog(Budget budgetToDelete) {
        // ... (All the dialog setup before fetching the category name)

        // *** FIX: Perform synchronous database access on a background thread ***
        new Thread(() -> {
            // Synchronously fetch the category name in the background
            String categoryName = getCategoryName(budgetToDelete.getCategoryId());

            // Once the name is fetched, switch back to the main thread to update the UI (the dialog message)
            requireActivity().runOnUiThread(() -> {
                View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.delete_confirmation_popup, null);

                TextView tvMessage = dialogView.findViewById(R.id.deleteMessage);
                Button btnCancel = dialogView.findViewById(R.id.cancelDeleteBtn);
                Button btnConfirm = dialogView.findViewById(R.id.confirmDeleteBtn);

                // 1. Set Custom Message
                String message = "Are you sure you want to delete the budget for '" + categoryName + "'?";
                tvMessage.setText(message);
                // -----------------------------

                AlertDialog dialog = new AlertDialog.Builder(getContext())
                        .setView(dialogView)
                        .setCancelable(true)
                        .create();

                // The dialog MUST be shown before setting window properties
                dialog.show();

                // ... (Rest of dialog customization and button listeners)

                // --- 2. Window Customization (Moved to after show() and using 70% width) ---
                Window window = dialog.getWindow();
                if (window != null) {
                    // Set transparent background
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                    // Set width to 70% of screen (CHANGED from 0.8)
                    int dialogWidth = (int)(requireActivity().getResources().getDisplayMetrics().widthPixels * 0.8);
                    window.setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT);
                }
                // --------------------------------------------------------

                btnCancel.setOnClickListener(v -> dialog.dismiss());

                btnConfirm.setOnClickListener(v -> {
                    // 🔑 Define the OnComplete callback (The Fix)
                    Runnable onComplete = () -> {
                        requireActivity().runOnUiThread(() -> {
                            loadBudgets(); // <-- This runs ONLY after the DB operation finishes
                            dialog.dismiss();
                            showCustomToast("Budget Deleted Successfully");
                        });
                    };

                    // The actual delete operation runs on a background thread
                    new Thread(() -> {
                        // 🔑 Pass the callback to the ViewModel
                        budgetViewModel.delete(budgetToDelete, onComplete);
                    }).start();
                });
            });
        }).start();
    }

    /**
     * Synchronously fetches the category name. Only call from a background thread or a helper method.
     */
    private String getCategoryName(int categoryId) {
        CategoryDAO categoryDao = FinixDatabase.getDatabase(requireContext()).categoryDao();
        List<Category> categories = categoryDao.getAllCategories();
        for (Category c : categories) {
            if (c.getLocalId() == categoryId) {
                return c.getName();
            }
        }
        return "Unknown Category";
    }


    private void loadMonthFilter(List<Budget> budgets) {
        // Find the latest budget start date to determine the default selection
        long latestDateMillis = 0;
        for (Budget b : budgets) {
            if (b.getStartDate() > latestDateMillis) {
                latestDateMillis = b.getStartDate();
            }
        }

        List<String> months = new ArrayList<>();
        // 1. "All" remains the first option
        months.add("All");

        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        String latestMonthYear = null;

        // 2. Populate the list with unique months and find the latest month string
        for (Budget b : budgets) {
            String month = sdf.format(new Date(b.getStartDate()));
            if (!months.contains(month)) {
                months.add(month);
            }
        }

        // 3. Determine the latest month string based on the latest date
        if (latestDateMillis > 0) {
            latestMonthYear = sdf.format(new Date(latestDateMillis));
        }

        // 4. Set the Adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                months
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerMonthFilter.setAdapter(adapter);

        // 5. Set the default selection to the latest month
        int defaultSelectionIndex = 0; // Default to "All" if no budgets or calculation fails
        if (latestMonthYear != null) {
            int latestMonthIndex = months.indexOf(latestMonthYear);
            if (latestMonthIndex != -1) {
                defaultSelectionIndex = latestMonthIndex;
            }
        }

        // Set the selection and trigger the filtering for the default month
        binding.spinnerMonthFilter.setSelection(defaultSelectionIndex);

        // 6. Set the Listener (This logic remains the same, but it will be triggered
        // by setSelection(defaultSelectionIndex) if it's called after the spinner is visible)
        binding.spinnerMonthFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // filterBudgetsByMonth will be called with the latest month when the fragment loads
                filterBudgetsByMonth(months.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void filterBudgetsByMonth(String month) {
        new Thread(() -> {
            List<Budget> allBudgets = budgetViewModel.getAllBudgets();
            List<Transaction> transactions = FinixDatabase.getDatabase(requireContext()).transactionDao().getAllTransactions();
            List<Category> categories = FinixDatabase.getDatabase(requireContext()).categoryDao().getAllCategories();

            if (!month.equals("All")) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
                List<Budget> filtered = new ArrayList<>();
                for (Budget b : allBudgets) {
                    if (sdf.format(new Date(b.getStartDate())).equals(month)) filtered.add(b);
                }
                allBudgets = filtered;
            }

            List<Budget> finalBudgets = allBudgets;
            requireActivity().runOnUiThread(() -> adapter.setData(finalBudgets, transactions, categories));
        }).start();
    }
}