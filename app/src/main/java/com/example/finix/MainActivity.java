package com.example.finix;

import static androidx.core.content.ContentProviderCompat.requireContext;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.finix.data.Budget;
import com.example.finix.data.Category;
import com.example.finix.data.CategoryDAO;
import com.example.finix.data.FinixDatabase;
import com.example.finix.data.Transaction;
import com.example.finix.databinding.ActivityMainBinding;
import com.example.finix.ui.budget.BudgetViewModel;
import com.example.finix.ui.transactions.TransactionsViewModel;

import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private TransactionsViewModel viewModel; // for saving transactions globally
    private long startDateMillis = 0;
    private long endDateMillis = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.appBarMain.toolbar);

        viewModel = new ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())
        ).get(TransactionsViewModel.class);

        // Quick FAB menu
        binding.appBarMain.addQuickMenu.setOnClickListener(this::showQuickAddPopup);

        DrawerLayout drawer = binding.drawerLayout;
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_dashboard, R.id.nav_transactions, R.id.nav_budget,
                R.id.nav_savings, R.id.nav_settings
        ).setOpenableLayout(drawer).build();
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);
    }

    private void showQuickAddPopup(View anchor) {
        View popupView = getLayoutInflater().inflate(R.layout.layout_quick_add_popup, null);
        final PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));

        // --- START: Animation Change ---
        // Use a built-in animation style (android.R.style.Animation_Toast for a quick fade/slide, or define your own)
        popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);
        // --- END: Animation Change ---

        // --- START: Positioning Logic ---
        int gap = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12,
                getResources().getDisplayMetrics()
        );

        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int fabX = location[0];
        int fabY = location[1];

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = popupView.getMeasuredWidth();
        int popupHeight = popupView.getMeasuredHeight();

        int xOffset = fabX + anchor.getWidth() - popupWidth;
        int yOffset = fabY + anchor.getHeight() - popupHeight;

        int leftShift = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, getResources().getDisplayMetrics());
        xOffset -= leftShift;
        xOffset -= gap;
        yOffset -= gap;
        // --- END: Positioning Logic ---

        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, xOffset, yOffset);

        popupView.findViewById(R.id.btnAddTransaction).setOnClickListener(v -> {
            popupWindow.dismiss();
            showAddTransactionDialog(null);
        });

        popupView.findViewById(R.id.btnAddBudget).setOnClickListener(v -> {
            popupWindow.dismiss();
            showAddBudgetDialog();
        });
        popupView.findViewById(R.id.btnAddGoal).setOnClickListener(v -> popupWindow.dismiss());
    }

    public void showAddTransactionDialog(Transaction transactionToEdit) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View popupView = inflater.inflate(R.layout.add_edit_transaction_popup, null);

        TextView tvTitle = popupView.findViewById(R.id.popupTitle);
        tvTitle.setText("Add Transaction"); // Only adding, no edit

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

        // --- Setup category AutoComplete ---
        List<String> categoriesList = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, categoriesList);
        actCategory.setAdapter(adapter);
        actCategory.setThreshold(0);

        Map<String, Integer> categoryNameToIdMap = new HashMap<>();

        // Observe categories from ViewModel
        viewModel.getCategoriesLive().observe(this, idToNameMap -> {
            categoriesList.clear(); // Clear the list for fresh data
            categoryNameToIdMap.clear();

            // 🆕 NEW: Add "Add New Category" first
            categoriesList.add("Add New Category");

            if (idToNameMap != null) {
                for (Map.Entry<Integer, String> entry : idToNameMap.entrySet()) {
                    String name = entry.getValue();
                    Integer id = entry.getKey();
                    // Ensure we don't accidentally add it twice if it existed as a real category name
                    if (!"Add New Category".equals(name)) {
                        categoriesList.add(name);
                        categoryNameToIdMap.put(name, id);
                    }
                }
            }

            // ❌ REMOVED: The previous check at the end is no longer needed.
            // if (!categoriesList.contains("Add New Category")) categoriesList.add("Add New Category");

            adapter.notifyDataSetChanged();
        });

        actCategory.setOnClickListener(v -> { if (!actCategory.isPopupShowing()) actCategory.showDropDown(); });
        actCategory.setOnItemClickListener((parent, v, pos, id) -> {
            if ("Add New Category".equals(adapter.getItem(pos))) {
                llAddCategory.setVisibility(View.VISIBLE);
                actCategory.setVisibility(View.GONE);
            }
        });

        btnSaveCategory.setOnClickListener(v -> {
            String newCat = etNewCategory.getText().toString().trim();
            if (!newCat.isEmpty()) {
                viewModel.addCategory(newCat);
                showCustomMessage("Information", "New Category Added ");

                actCategory.postDelayed(() -> {
                    actCategory.setText(newCat);
                    actCategory.setVisibility(View.VISIBLE);
                    llAddCategory.setVisibility(View.GONE);
                    actCategory.clearFocus(); // ensures fresh reload next focus
                }, 200);

                etNewCategory.setText("");
            } else {
                showCustomMessage("Error", "Category Can not be Empty ");
            }
        });


        btnBackCategory.setOnClickListener(v -> {
            llAddCategory.setVisibility(View.GONE);
            actCategory.setVisibility(View.VISIBLE);
        });

        btnPickDateTime.setOnClickListener(v -> openDateTimePicker(tvDateTime));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(popupView).create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnSave.setOnClickListener(v -> {
            String amountText = etAmount.getText().toString().trim();
            String desc = etDescription.getText().toString().trim(); // Description
            String catName = actCategory.getText().toString().trim(); // Category NAME
            String dateText = tvDateTime.getText().toString().trim();

            // Get the ID of the checked radio button. Returns -1 if none is checked.
            int checkedTypeId = rgType.getCheckedRadioButtonId();
            String type = checkedTypeId == R.id.rbIncome ? "Income" : "Expense"; // Type only determined if one is selected

            // 1. CHECK REQUIRED FIELDS (Amount, Category Name, Date Text, Description)
            if (amountText.isEmpty() || catName.isEmpty() || dateText.isEmpty() || desc.isEmpty()) {
                showCustomMessage("Error", "Fill All Fields ");
                return;
            }

            // 2. CHECK TRANSACTION TYPE SELECTION
            if (checkedTypeId == -1) {
                showCustomMessage("Error", "Please select transaction type (Income or Expense)! ");
                return;
            }

            // 3. CHECK VALID CATEGORY SELECTION
            if (!categoryNameToIdMap.containsKey(catName)) {
                showCustomMessage("Error", "Invalid Category Selected ");
                return;
            }
            int categoryId = categoryNameToIdMap.get(catName);

            try {
                double amount = Double.parseDouble(amountText);
                long dateMillis = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .parse(dateText).getTime();

                // Save new transaction
                viewModel.saveTransaction(amount, type, categoryId, dateMillis, desc, dialog::dismiss);
                showCustomMessage("Information", "New Transaction Added ");

                // No need to call dialog.dismiss() again here, as it's passed as a callback
                // to viewModel.saveTransaction and should be executed upon completion/success.

            } catch (Exception e) {
                showCustomMessage("Error", "Invalid Amount ot Date! ");
            }
        });

        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }


    private void openDateTimePicker(TextView tvDateTime) {
        final Calendar calendar = Calendar.getInstance();
        if (!tvDateTime.getText().toString().isEmpty()) {
            try {
                calendar.setTime(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .parse(tvDateTime.getText().toString()));
            } catch (Exception ignored) {
            }
        }

        // --- Define Colors and Ripples ---
        final int colorPositive = Color.parseColor("#00BFA5"); // Green-ish
        final int colorNegative = Color.parseColor("#FF5252"); // Red-ish
        final ColorStateList ripplePositive = ColorStateList.valueOf(Color.parseColor("#3300BFA5"));
        final ColorStateList rippleNegative = ColorStateList.valueOf(Color.parseColor("#33FF5252"));

        // 1. DatePickerDialog
        DatePickerDialog dp = new DatePickerDialog(this,
                (view, y, m, d) -> {
                    calendar.set(y, m, d);

                    // 2. TimePickerDialog (Called after date is picked)
                    TimePickerDialog tp = new TimePickerDialog(this,
                            (timeView, h, min) -> {
                                calendar.set(Calendar.HOUR_OF_DAY, h);
                                calendar.set(Calendar.MINUTE, min);
                                tvDateTime.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                        .format(calendar.getTime()));
                            }, calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE), true);

                    // Apply customization to TimePickerDialog buttons
                    tp.setOnShowListener(dialogInterface -> {
                        // OK Button (Positive)
                        tp.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(colorPositive);
                        tp.getButton(DialogInterface.BUTTON_POSITIVE).setBackground(new RippleDrawable(ripplePositive, null, null));
                        // Cancel Button (Negative)
                        tp.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(colorNegative);
                        tp.getButton(DialogInterface.BUTTON_NEGATIVE).setBackground(new RippleDrawable(rippleNegative, null, null));
                    });

                    tp.show();

                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        // Apply customization to DatePickerDialog buttons
        dp.setOnShowListener(dialogInterface -> {
            // OK Button (Positive)
            dp.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(colorPositive);
            dp.getButton(DialogInterface.BUTTON_POSITIVE).setBackground(new RippleDrawable(ripplePositive, null, null));
            // Cancel Button (Negative)
            dp.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(colorNegative);
            dp.getButton(DialogInterface.BUTTON_NEGATIVE).setBackground(new RippleDrawable(rippleNegative, null, null));
        });

        dp.show();
    }

    private void openDatePicker(TextView tvDate, boolean isStartDate) {
        final Calendar calendar = Calendar.getInstance();

        DatePickerDialog dp = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    tvDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime()));

                    if (isStartDate) {
                        startDateMillis = calendar.getTimeInMillis();
                    } else {
                        endDateMillis = calendar.getTimeInMillis();
                    }
                },
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
        );
        dp.show();
    }

    private void showCustomToast(String message) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View layout = inflater.inflate(R.layout.custom_message, null);
        TextView toastMessage = layout.findViewById(R.id.toast_message);
        ImageView close = layout.findViewById(R.id.toast_close);
        ProgressBar progressBar = layout.findViewById(R.id.toast_progress);

        toastMessage.setText(message);
        progressBar.setProgress(100);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(layout).create();
        close.setOnClickListener(v -> dialog.dismiss());

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setDimAmount(0f);
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.TOP;
            params.y = 50;
            dialog.getWindow().setAttributes(params);
        }

        dialog.show();

        new android.os.CountDownTimer(3000, 50) {
            public void onTick(long millisUntilFinished) {
                int progress = (int) Math.max(0, Math.round((millisUntilFinished / 3000.0) * 100));
                progressBar.setProgress(progress);
            }

            public void onFinish() {
                if (dialog.isShowing()) dialog.dismiss();
            }
        }.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration) || super.onSupportNavigateUp();
    }

    private void showCustomMessage(String title, String message) {
        // Inflate the custom layout
        View layout = LayoutInflater.from(this).inflate(R.layout.custom_message, null);

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
        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
    }

    public void showAddBudgetDialog() {
        try{

            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_budget, null);

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

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    .create();

            // --- Load categories ---
            new Thread(() -> {
                try {
                    List<Category> categoryList = FinixDatabase.getDatabase(this).categoryDao().getAllCategories();
                    List<String> categoryNames = new ArrayList<>();
                    for (Category c : categoryList) categoryNames.add(c.getName());
                    categoryNames.add("+ Add New Category");

                    runOnUiThread(() -> {
                        try {
                            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                    this,
                                    android.R.layout.simple_dropdown_item_1line,
                                    categoryNames
                            );
                            etCategory.setAdapter(adapter);

                            etCategory.setOnClickListener(v -> etCategory.showDropDown());
                            etCategory.setOnItemClickListener((parent, view, position, id) -> {
                                String selected = categoryNames.get(position);
                                if (selected.equals("+ Add New Category")) {
                                    etCategory.setVisibility(View.GONE);
                                    llAddNewCategory.setVisibility(View.VISIBLE);
                                } else {
                                    llAddNewCategory.setVisibility(View.GONE);
                                }
                            });
                        } catch (Exception e) {
                            showCustomMessage("Error", "Failed to load categories: " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> showCustomMessage("Error", "Failed to fetch categories: " + e.getMessage()));
                }
            }).start();


            // --- Date pickers ---
            btnPickDate1.setOnClickListener(v -> openDatePicker(tvStartDate, true));
            btnPickDate2.setOnClickListener(v -> openDatePicker(tvEndDate, false));

            // --- Save new category ---
            btnSaveCategory.setOnClickListener(v -> {
                String newCat = etNewCategory.getText().toString().trim();
                if (!newCat.isEmpty()) {
                    new Thread(() -> {
                        try {
                            CategoryDAO dao = FinixDatabase.getDatabase(this).categoryDao();
                            dao.insert(new Category(newCat));
                            runOnUiThread(() -> {
                                showCustomMessage("Info", "Category Added");
                                llAddNewCategory.setVisibility(View.GONE);
                                etCategory.setVisibility(View.VISIBLE);
                                etCategory.setText(newCat);
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> showCustomMessage("Error", "Failed to add category: " + e.getMessage()));
                        }
                    }).start();
                } else showCustomMessage("Error", "Category cannot be empty");
            });

            btnCancelCategory.setOnClickListener(v -> {
                llAddNewCategory.setVisibility(View.GONE);
                etCategory.setVisibility(View.VISIBLE);
            });

            // --- Save Budget ---
            btnSave.setOnClickListener(v -> {
                try {
                    String catName = etCategory.getText().toString().trim();
                    String amountText = etAmount.getText().toString().trim();
                    if (catName.isEmpty() || amountText.isEmpty()) {
                        showCustomMessage("Error", "Fill all fields");
                        return;
                    }

                    double amount;
                    try {
                        amount = Double.parseDouble(amountText);
                    } catch (NumberFormatException e) {
                        showCustomMessage("Error", "Invalid amount entered");
                        return;
                    }

                    new Thread(() -> {
                        try {
                            List<Category> allCats = FinixDatabase.getDatabase(this).categoryDao().getAllCategories();
                            int catId = -1;
                            for (Category c : allCats) {
                                if (c.getName().equalsIgnoreCase(catName)) {
                                    catId = c.getId();
                                    break;
                                }
                            }
                            if (catId == -1) {
                                runOnUiThread(() -> showCustomMessage("Error", "Selected category not found"));
                                return;
                            }

                            Budget budget = new Budget(catId, amount, startDateMillis, endDateMillis);
                            new BudgetViewModel(getApplication()).insert(budget);

                            runOnUiThread(() -> {
                                dialog.dismiss();
                                showCustomMessage("Info", "Budget Added Successfully");
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> showCustomMessage("Error", "Failed to save budget: " + e.getMessage()));
                        }
                    }).start();

                } catch (Exception e) {
                    showCustomMessage("Error", "Unexpected error: " + e.getMessage());
                }
            });

            dialog.show();
        } catch (Exception e) {
            showCustomMessage("Error", "Failed to open budget dialog: " + e.getMessage());
        }

    }
}
