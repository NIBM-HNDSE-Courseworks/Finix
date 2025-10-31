package com.example.finix.ui.budget;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.finix.R;
import com.example.finix.data.Budget;
import com.example.finix.data.Category;
import com.example.finix.data.CategoryDAO;
import com.example.finix.data.FinixDatabase;
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

        binding.recyclerBudgets.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerBudgets.setAdapter(adapter);

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
                adapter.setData(budgets, transactions, categories);
                loadMonthFilter(budgets);
            });
        }).start();
    }

    private void showAddBudgetDialog(Budget budgetToEdit) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_budget, null);

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

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();


        // Load categories for AutoComplete
        new Thread(() -> {
            CategoryDAO categoryDao = FinixDatabase.getDatabase(requireContext()).categoryDao();
            List<Category> categoryList = categoryDao.getAllCategories();
            List<String> categoryNames = new ArrayList<>();

            for (Category c : categoryList) categoryNames.add(c.getName());
            categoryNames.add("+ Add New Category");

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
        if (budgetToEdit != null) { // <-- New block for edit
            etAmount.setText(String.valueOf(budgetToEdit.getBudgetedAmount()));
            startDateMillis = budgetToEdit.getStartDate();
            endDateMillis = budgetToEdit.getEndDate();
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            tvStartDate.setText(sdf.format(startDateMillis));
            tvEndDate.setText(sdf.format(endDateMillis));

            new Thread(() -> {
                List<Category> categories = FinixDatabase.getDatabase(requireContext()).categoryDao().getAllCategories();
                for (Category c : categories) {
                    if (c.getId() == budgetToEdit.getCategoryId()) {
                        String catName = c.getName();
                        requireActivity().runOnUiThread(() -> etCategory.setText(catName));
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

                    // Insert the new category
                    categoryDao.insert(new Category(newCategoryName));

                    // Fetch updated categories
                    List<Category> updated = categoryDao.getAllCategories();
                    List<String> updatedNames = new ArrayList<>();
                    for (Category c : updated) updatedNames.add(c.getName());
                    updatedNames.add("+ Add New Category");

                    // Update UI on main thread
                    requireActivity().runOnUiThread(() -> {
                        showCustomMessage("Information", "Category Added ");

                        // Reset visibility
                        llAddNewCategory.setVisibility(View.GONE);
                        etCategory.setVisibility(View.VISIBLE);
                        etCategory.setText(newCategoryName);

                        // Reload dropdown
                        ArrayAdapter<String> newAdapter = new ArrayAdapter<>(
                                requireContext(),
                                android.R.layout.simple_dropdown_item_1line,
                                updatedNames
                        );
                        etCategory.setAdapter(newAdapter);
                    });

                } catch (Exception e) {
                    // Catch any DB or runtime exception
                    requireActivity().runOnUiThread(() ->
                            showCustomMessage("Error", "Error adding category: " + e.getMessage())
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


        // Save Budget
        btnSave.setOnClickListener(v -> {
            String categoryName = etCategory.getText().toString().trim();
            String amountText = etAmount.getText().toString().trim();

            if (categoryName.isEmpty() || amountText.isEmpty() || startDateMillis == 0 || endDateMillis == 0) {
                showCustomMessage("Error", "Fill all Feilds ");
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountText);
            } catch (NumberFormatException e) {
                showCustomMessage("Error", "Invalid Amount : " + e.getMessage());
                return;
            }

            //Limit the budget amount to 999,999
            if (amount > 999999) {
                requireActivity().runOnUiThread(() ->
                        showCustomMessage("Error", "Amount cannot exceed Rs 999,999"));
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
                        Category newCat = new Category(categoryName);
                        categoryDao.insert(newCat);

                        // Fetch latest category
                        List<Category> updated = categoryDao.getAllCategories();
                        categoryId = updated.get(updated.size() - 1).getId();
                    } else {
                        categoryId = matchingCategory.getId();
                    }

                    if (budgetToEdit != null) { // <-- Edit mode
                        budgetToEdit.setCategoryId(categoryId);
                        budgetToEdit.setBudgetedAmount(amount);
                        budgetToEdit.setStartDate(startDateMillis);
                        budgetToEdit.setEndDate(endDateMillis);
                        budgetViewModel.update(budgetToEdit); // <-- ensure ViewModel has update()
                    } else { // Add mode
                        Budget newBudget = new Budget(categoryId, amount, startDateMillis, endDateMillis);
                        budgetViewModel.insert(newBudget);
                    }

                    requireActivity().runOnUiThread(() -> {
                        loadBudgets();
                        dialog.dismiss();
                        showCustomMessage("Information", "Budged Added Sucessfully ");
                    });

                } catch (Exception e) {
                    requireActivity().runOnUiThread(() ->
                            showCustomMessage("Error", "Error Saving Budget: " + e.getMessage())
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
                    calendar.set(year, month, dayOfMonth, 0, 0);
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
    }

    private void showCustomMessage(String title, String message) {
        // Inflate the custom layout
        View layout = LayoutInflater.from(requireContext()).inflate(R.layout.custom_message, null);

        ImageView icon = layout.findViewById(R.id.toast_icon);
        TextView infoLabel = layout.findViewById(R.id.info_label);
        TextView toastMessage = layout.findViewById(R.id.toast_message);
        ImageView closeBtn = layout.findViewById(R.id.toast_close);
        ProgressBar progressBar = layout.findViewById(R.id.toast_progress);

        infoLabel.setText(title);
        toastMessage.setText(message);
        progressBar.setProgress(100); // Can be animated if needed

        // Optional: Close button
        closeBtn.setOnClickListener(v -> {
            if (layout.getParent() instanceof android.view.ViewGroup) {
                ((ViewGroup) layout.getParent()).removeView(layout);
            }
        });

        // Create and show toast
        Toast toast = new Toast(requireContext());
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
    }

    // --- NEW: Delete Confirmation Dialog ---
    private void showDeleteConfirmationDialog(Budget budgetToDelete) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.delete_confirmation_popup, null);

        Button btnCancel = dialogView.findViewById(R.id.cancelDeleteBtn);
        Button btnConfirm = dialogView.findViewById(R.id.confirmDeleteBtn);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            new Thread(() -> {
                budgetViewModel.delete(budgetToDelete); // <-- Delete action
                requireActivity().runOnUiThread(() -> {
                    loadBudgets();
                    dialog.dismiss();
                    showCustomMessage("Information", "Budget Deleted Successfully");
                });
            }).start();
        });

        dialog.show();
    }

    private void loadMonthFilter(List<Budget> budgets) {
        List<String> months = new ArrayList<>();
        months.add("All"); // default option

        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        for (Budget b : budgets) {
            String month = sdf.format(new Date(b.getStartDate()));
            if (!months.contains(month)) months.add(month);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                months
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerMonthFilter.setAdapter(adapter);

        binding.spinnerMonthFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
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
