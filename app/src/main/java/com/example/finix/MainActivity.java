package com.example.finix;

import static androidx.core.content.ContentProviderCompat.requireContext;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
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
import com.example.finix.data.SynchronizationLog;
import com.example.finix.data.Transaction;
import com.example.finix.databinding.ActivityMainBinding;
import com.example.finix.ui.budget.BudgetViewModel;
import com.example.finix.ui.transactions.TransactionsViewModel;
import com.example.finix.data.SavingsGoal;
import com.example.finix.ui.savings.SavingsGoalsViewModel;

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

        // 🟢 MODIFIED: Call the new goal dialog method directly
        popupView.findViewById(R.id.btnAddGoal).setOnClickListener(v -> {
            popupWindow.dismiss();
            showAddGoalDialog();
        });
    }

    public void showAddTransactionDialog(Transaction transactionToEdit) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View popupView = inflater.inflate(R.layout.add_edit_transaction_popup, null);

        TextView tvTitle = popupView.findViewById(R.id.popupTitle);
        tvTitle.setText("Add Transaction");

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

        etAmount.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        etNewCategory.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});

        // Setup category AutoComplete
        List<String> categoriesList = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, categoriesList);
        actCategory.setAdapter(adapter);
        actCategory.setThreshold(0);

        Map<String, Integer> categoryNameToIdMap = new HashMap<>();

        // Observe categories from ViewModel
        viewModel.getCategoriesLive().observe(this, idToNameMap -> {
            categoriesList.clear();
            categoryNameToIdMap.clear();

            // Add +Add New Category
            categoriesList.add("+Add New Category");

            if (idToNameMap != null) {
                for (Map.Entry<Integer, String> entry : idToNameMap.entrySet()) {
                    String name = entry.getValue();
                    Integer localId = entry.getKey(); // <-- already the local_id
                    if (!"+Add New Category".equals(name)) {
                        categoriesList.add(name);
                        categoryNameToIdMap.put(name, localId); // ✅ use the map key (local_id)
                    }
                }
            }
            adapter.notifyDataSetChanged();
        });

        actCategory.setOnClickListener(v -> {
            if (!actCategory.isPopupShowing()) actCategory.showDropDown();
        });

        actCategory.setOnItemClickListener((parent, v, pos, id) -> {
            if ("+Add New Category".equals(adapter.getItem(pos))) {
                llAddCategory.setVisibility(View.VISIBLE);
                actCategory.setVisibility(View.GONE);
                etNewCategory.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(etNewCategory, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        btnSaveCategory.setOnClickListener(v -> {
            String newCat = etNewCategory.getText().toString().trim();
            if (newCat.isEmpty()) {
                showCustomToast("Category cannot be empty");
                return;
            }

            // Call ViewModel method to add the category and sync
            viewModel.addCategoryWithSync(newCat);

            // Show confirmation toast
            showCustomToast("New category added!");

            // Delay briefly to let LiveData update and refresh UI
            actCategory.postDelayed(() -> {
                // Set the newly added category in the AutoCompleteTextView
                actCategory.setText(newCat);
                actCategory.setVisibility(View.VISIBLE);
                llAddCategory.setVisibility(View.GONE);
                actCategory.clearFocus();
            }, 200);

            // Clear the input field for next entry
            etNewCategory.setText("");
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
            String desc = etDescription.getText().toString().trim();
            String catName = actCategory.getText().toString().trim();
            String dateText = tvDateTime.getText().toString().trim();

            int checkedTypeId = rgType.getCheckedRadioButtonId();
            String type = checkedTypeId == R.id.rbIncome ? "Income" : "Expense";

            if (amountText.isEmpty() || catName.isEmpty() || dateText.isEmpty() || desc.isEmpty()) {
                showCustomToast("Fill All Fields");
                return;
            }

            if (checkedTypeId == -1) {
                showCustomToast("Select Transaction Type!");
                return;
            }

            // Make sure the category exists in our map
            if (!categoryNameToIdMap.containsKey(catName)) {
                showCustomToast("Invalid Category Selected");
                return;
            }

            // ✅ Use local_id for the foreign key
            int categoryLocalId = categoryNameToIdMap.get(catName);

            try {
                double amount = Double.parseDouble(amountText);
                long dateMillis = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .parse(dateText).getTime();

                // Pass local_id to saveTransaction
                viewModel.saveTransaction(amount, type, categoryLocalId, dateMillis, desc, dialog::dismiss);
                showCustomToast("New Transaction Added");
            } catch (Exception e) {
                showCustomToast("Invalid Amount or Date!");
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

    // In MainActivity.java

    private void openDatePicker(TextView tvDate, boolean isStartDate) {
        final Calendar calendar = Calendar.getInstance();

        // --- Define Colors and Ripples ---
        final int colorPositive = Color.parseColor("#00BFA5"); // Teal color for OK button
        final int colorNegative = Color.parseColor("#FF5252"); // Red color for CANCEL button
        final ColorStateList ripplePositive = ColorStateList.valueOf(Color.parseColor("#3300BFA5")); // Light teal ripple
        final ColorStateList rippleNegative = ColorStateList.valueOf(Color.parseColor("#33FF5252")); // Light red ripple
        // ---------------------------------

        DatePickerDialog dp = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth, 0, 0, 0); // Set time to midnight for budget comparison
                    calendar.set(Calendar.MILLISECOND, 0);

                    tvDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime()));

                    if (isStartDate) {
                        startDateMillis = calendar.getTimeInMillis();
                    } else {
                        endDateMillis = calendar.getTimeInMillis();
                    }
                },
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
        );

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

    private void showCustomToast(String message) { // Method to show custom toast dialog
        LayoutInflater inflater = getLayoutInflater(); // Get inflater to load custom layout
        // 🚨 CRITICAL FIX: To allow requireContext() in a non-Fragment context, we use 'this'
        View layout = inflater.inflate(R.layout.custom_message, null); // Inflate the custom toast layout

        TextView text = layout.findViewById(R.id.toast_message); // Get TextView for message
        ImageView close = layout.findViewById(R.id.toast_close); // Get close button ImageView
        ProgressBar progressBar = layout.findViewById(R.id.toast_progress); // Get ProgressBar

        text.setText(message); // Set the toast message text
        progressBar.setProgress(100); // Initialize progress bar to full

        // 🚨 CRITICAL FIX: Replacing requireContext() with 'this' (the Activity context)
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(layout) // Set custom layout
                .create(); // Create dialog instance

        close.setOnClickListener(v -> dialog.dismiss()); // Close dialog on button click

        if (dialog.getWindow() != null) { // Check if window exists
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); // Make background transparent
            dialog.getWindow().setDimAmount(0f); // Remove dim behind dialog

            WindowManager.LayoutParams params = dialog.getWindow().getAttributes(); // Get window attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT; // Set width to match parent
            params.height = WindowManager.LayoutParams.WRAP_CONTENT; // Set height to wrap content

            // Set gravity to BOTTOM
            params.gravity = android.view.Gravity.BOTTOM; // Position at bottom

            // Set offset from bottom in pixels (use the same offset for consistency)
            params.y = 50;

            dialog.getWindow().setAttributes(params); // Apply attributes
        }

        dialog.show(); // Show the dialog

        new CountDownTimer(3000, 50) { // Timer for auto-dismiss: 3s, tick every 50ms
            public void onTick(long millisUntilFinished) { // Called on every tick
                int progress = (int) ((millisUntilFinished / 3000.0) * 100); // Calculate progress
                progressBar.setProgress(progress); // Update progress bar
            }
            public void onFinish() { // Called when timer finishes
                if (dialog.isShowing()) dialog.dismiss(); // Dismiss dialog
            }
        }.start(); // Start timer
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

    public void showAddBudgetDialog() {
        try {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_budget, null);
            AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

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

            // --- Load categories ---
            new Thread(() -> {
                try {
                    List<Category> categoryList = FinixDatabase.getDatabase(this).categoryDao().getAllCategories();
                    List<String> categoryNames = new ArrayList<>();

                    categoryNames.add("+ Add New Category"); // special option
                    for (Category c : categoryList) categoryNames.add(c.getName());

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

                                    // 🎯 Auto focus and show keyboard
                                    etNewCategory.requestFocus();
                                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                    imm.showSoftInput(etNewCategory, InputMethodManager.SHOW_IMPLICIT);
                                } else {
                                    llAddNewCategory.setVisibility(View.GONE);
                                }
                            });
                        } catch (Exception e) {
                            showCustomToast("Failed to load categories: " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> showCustomToast("Failed to fetch categories: " + e.getMessage()));
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
                            FinixDatabase db = FinixDatabase.getDatabase(this);

                            // 1️⃣ Insert category into DB
                            Category category = new Category(newCat);
                            long localId = db.categoryDao().insert(category);

                            // 2️⃣ Create sync log entry for pending upload
                            SynchronizationLog log = new SynchronizationLog(
                                    "categories",
                                    (int) localId,
                                    System.currentTimeMillis(),
                                    "PENDING"
                            );
                            db.synchronizationLogDao().insert(log); // ✅ correct DAO name

                            // 3️⃣ Update UI
                            runOnUiThread(() -> {
                                showCustomToast("Category Added");
                                llAddNewCategory.setVisibility(View.GONE);
                                etCategory.setVisibility(View.VISIBLE);
                                etCategory.setText(newCat);
                            });

                        } catch (Exception e) {
                            runOnUiThread(() -> showCustomToast("Failed to add category: " + e.getMessage()));
                        }
                    }).start();
                } else {
                    showCustomToast("Category cannot be empty");
                }
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
                        showCustomToast("Fill all fields");
                        return;
                    }

                    double amount;
                    try {
                        amount = Double.parseDouble(amountText);
                    } catch (NumberFormatException e) {
                        showCustomToast("Invalid amount entered");
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
                                runOnUiThread(() -> showCustomToast("Selected category not found"));
                                return;
                            }

                            Budget budget = new Budget(catId, amount, startDateMillis, endDateMillis);
                            new BudgetViewModel(getApplication()).insert(budget);

                            runOnUiThread(() -> {
                                dialog.dismiss();
                                showCustomToast("Budget Added Successfully");
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> showCustomToast("Failed to save budget: " + e.getMessage()));
                        }
                    }).start();

                } catch (Exception e) {
                    showCustomToast("Unexpected error: " + e.getMessage());
                }
            });

            dialog.show();
        } catch (Exception e) {
            showCustomToast("Failed to open budget dialog: " + e.getMessage());
        }
    }


    // --- Goal-specific Date Pickers ---

    private interface DatePickCallback {
        void onPicked(String date);
    }

    private void openGoalDatePicker(DatePickCallback cb) {
        final Calendar calendar = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        // Define Colors
        final int colorPositive = Color.parseColor("#00BFA5"); // Teal
        final int colorNegative = Color.parseColor("#FF5252"); // Red

        DatePickerDialog dp = new DatePickerDialog(
                this,
                (view, y, m, d) -> {
                    calendar.set(y, m, d);
                    String selectedDate = format.format(calendar.getTime());
                    cb.onPicked(selectedDate);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        // Apply customization to DatePickerDialog buttons
        dp.setOnShowListener(dialogInterface -> {
            dp.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(colorPositive);
            dp.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(colorNegative);
        });

        dp.show();
    }

    private long parseGoalDateToMillis(String ddMMyyyy) {
        try {
            Date parsed = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(ddMMyyyy);
            return (parsed != null) ? parsed.getTime() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    // 🟢 NEW: Add Goal Popup Logic
    public void showAddGoalDialog() {
        try {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_goal, null);
            AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            AutoCompleteTextView actCategory = dialogView.findViewById(R.id.etCategory);
            LinearLayout llAddCategory = dialogView.findViewById(R.id.llAddNewCategory);
            EditText etNewCategory = dialogView.findViewById(R.id.etNewCategory);
            Button btnSaveCategory = dialogView.findViewById(R.id.btnSaveCategory);
            Button btnCancelCategory = dialogView.findViewById(R.id.btnCancelCategory);

            EditText etGoalName = dialogView.findViewById(R.id.etGoalName);
            EditText etGoalDescription = dialogView.findViewById(R.id.etGoalDescription);
            EditText etTargetAmount = dialogView.findViewById(R.id.etTargetAmount);
            TextView tvTargetDate = dialogView.findViewById(R.id.etTargetDate);
            ImageButton btnPickDate = dialogView.findViewById(R.id.btnPickDate);
            Button btnSave = dialogView.findViewById(R.id.btnSaveGoal);

            List<String> categoryNames = new ArrayList<>();
            Map<String, Integer> nameToId = new HashMap<>();

            new Thread(() -> {
                try {
                    List<Category> categoryList = FinixDatabase.getDatabase(this).categoryDao().getAllCategories();
                    categoryNames.add("+ Add New Category");
                    for (Category c : categoryList) {
                        categoryNames.add(c.getName());
                        nameToId.put(c.getName(), c.getId());
                    }

                    runOnUiThread(() -> {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, categoryNames);
                        actCategory.setAdapter(adapter);
                        actCategory.setOnClickListener(v -> actCategory.showDropDown());
                        actCategory.setOnItemClickListener((parent, view, position, id) -> {
                            String selected = categoryNames.get(position);
                            if (selected.equals("+ Add New Category")) {
                                llAddCategory.setVisibility(View.VISIBLE);
                                actCategory.setVisibility(View.GONE);

                                // ✅ Auto-focus & show keyboard
                                etNewCategory.requestFocus();
                                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                if (imm != null) imm.showSoftInput(etNewCategory, InputMethodManager.SHOW_IMPLICIT);
                            } else {
                                llAddCategory.setVisibility(View.GONE);
                            }
                        });
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> showCustomToast("Failed to load categories: " + e.getMessage()));
                }
            }).start();

            View.OnClickListener pickDate = v -> openGoalDatePicker(date -> tvTargetDate.setText(date));
            tvTargetDate.setOnClickListener(pickDate);
            btnPickDate.setOnClickListener(pickDate);

            // --- Save new category ---
            btnSaveCategory.setOnClickListener(v -> {
                String newCat = etNewCategory.getText().toString().trim();
                if (!newCat.isEmpty()) {
                    new Thread(() -> {
                        try {
                            FinixDatabase db = FinixDatabase.getDatabase(this);

                            // 1️⃣ Insert category into DB
                            Category category = new Category(newCat);
                            long localId = db.categoryDao().insert(category);

                            // 2️⃣ Create sync log entry (PENDING state)
                            SynchronizationLog log = new SynchronizationLog(
                                    "categories",
                                    (int) localId,
                                    System.currentTimeMillis(),
                                    "PENDING"
                            );
                            db.synchronizationLogDao().insert(log); // ✅ correct DAO

                            // 3️⃣ Refresh category list in UI
                            List<Category> updatedList = db.categoryDao().getAllCategories();
                            categoryNames.clear();
                            nameToId.clear();
                            categoryNames.add("+ Add New Category");
                            for (Category c : updatedList) {
                                categoryNames.add(c.getName());
                                nameToId.put(c.getName(), c.getId());
                            }

                            // 4️⃣ Update UI
                            runOnUiThread(() -> {
                                actCategory.setVisibility(View.VISIBLE);
                                llAddCategory.setVisibility(View.GONE);
                                actCategory.setText(newCat);
                                showCustomToast("Category Added");
                            });

                        } catch (Exception e) {
                            runOnUiThread(() -> showCustomToast("Failed to add category: " + e.getMessage()));
                        }
                    }).start();
                } else {
                    showCustomToast("Category cannot be empty");
                }
            });

            btnCancelCategory.setOnClickListener(v -> {
                llAddCategory.setVisibility(View.GONE);
                actCategory.setVisibility(View.VISIBLE);
            });

            btnSave.setOnClickListener(v -> {
                String catName = actCategory.getText().toString().trim();
                String goalName = etGoalName.getText().toString().trim();
                String desc = etGoalDescription.getText().toString().trim();
                String amountStr = etTargetAmount.getText().toString().trim();
                String dateStr = tvTargetDate.getText().toString().trim();

                if (catName.isEmpty() || goalName.isEmpty() || amountStr.isEmpty() || dateStr.isEmpty()) {
                    showCustomToast("Please fill all fields");
                    return;
                }

                if (!nameToId.containsKey(catName)) {
                    showCustomToast("Invalid category!");
                    return;
                }

                double targetAmount;
                try {
                    targetAmount = Double.parseDouble(amountStr);
                } catch (NumberFormatException e) {
                    showCustomToast("Invalid amount entered");
                    return;
                }

                long targetDateMillis = parseGoalDateToMillis(dateStr);
                if (targetDateMillis == -1) {
                    showCustomToast("Invalid date format (dd/MM/yyyy)");
                    return;
                }

                int categoryId = nameToId.get(catName);
                new Thread(() -> {
                    try {
                        SavingsGoal goal = new SavingsGoal(categoryId, goalName, desc, targetAmount, targetDateMillis);
                        new SavingsGoalsViewModel(getApplication()).insert(goal);

                        runOnUiThread(() -> {
                            dialog.dismiss();
                            showCustomToast("Goal Added Successfully");
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showCustomToast("Failed to save goal: " + e.getMessage()));
                    }
                }).start();
            });

            dialog.show();
        } catch (Exception e) {
            showCustomToast("Failed to open goal dialog: " + e.getMessage());
        }
    }


}