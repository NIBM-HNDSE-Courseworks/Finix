package com.example.finix;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
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

import com.example.finix.data.Transaction;
import com.example.finix.databinding.ActivityMainBinding;
import com.example.finix.ui.transactions.TransactionsViewModel;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private TransactionsViewModel viewModel; // for saving transactions globally

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

        popupView.findViewById(R.id.btnAddBudget).setOnClickListener(v -> popupWindow.dismiss());
        popupView.findViewById(R.id.btnAddGoal).setOnClickListener(v -> popupWindow.dismiss());
    }

    public void showAddTransactionDialog(Transaction transactionToEdit) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View popupView = inflater.inflate(R.layout.add_edit_transaction_popup, null);

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

        List<String> categoriesList = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, categoriesList);
        actCategory.setAdapter(adapter);
        actCategory.setThreshold(0);

        viewModel.getCategoriesLive().observe(this, list -> {
            categoriesList.clear();
            categoriesList.addAll(list);
            if (!categoriesList.contains("Add New Category")) categoriesList.add("Add New Category");
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
                actCategory.setText(newCat);
                llAddCategory.setVisibility(View.GONE);
                actCategory.setVisibility(View.VISIBLE);
            } else showCustomToast("Category cannot be empty!");
        });

        btnBackCategory.setOnClickListener(v -> {
            llAddCategory.setVisibility(View.GONE);
            actCategory.setVisibility(View.VISIBLE);
        });

        btnPickDateTime.setOnClickListener(v -> openDateTimePicker(tvDateTime));

        if (transactionToEdit != null) {
            etAmount.setText(String.valueOf(transactionToEdit.getAmount()));
            etDescription.setText(transactionToEdit.getDescription());
            actCategory.setText(transactionToEdit.getCategory());
            tvDateTime.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(new Date(transactionToEdit.getDateTime())));
            if ("Income".equals(transactionToEdit.getType())) rgType.check(R.id.rbIncome);
            else rgType.check(R.id.rbExpense);
        }

        AlertDialog dialog = new AlertDialog.Builder(this).setView(popupView).create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnSave.setOnClickListener(v -> {
            String amountText = etAmount.getText().toString().trim();
            String desc = etDescription.getText().toString().trim();
            String cat = actCategory.getText().toString().trim();
            String dateText = tvDateTime.getText().toString().trim();
            String type = rgType.getCheckedRadioButtonId() == R.id.rbIncome ? "Income" : "Expense";

            if (amountText.isEmpty() || cat.isEmpty() || dateText.isEmpty()) {
                showCustomToast("Fill all fields!");
                return;
            }

            try {
                double amount = Double.parseDouble(amountText);
                long dateMillis = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .parse(dateText).getTime();

                if (transactionToEdit != null) {
                    transactionToEdit.setAmount(amount);
                    transactionToEdit.setDescription(desc);
                    transactionToEdit.setCategory(cat);
                    transactionToEdit.setDateTime(dateMillis);
                    transactionToEdit.setType(type);
                    viewModel.updateTransaction(transactionToEdit);
                    showCustomToast("Transaction updated!");
                } else {
                    viewModel.saveTransaction(amount, type, cat, dateMillis, desc);
                    showCustomToast("New transaction added!");
                }
                dialog.dismiss();
            } catch (Exception e) {
                showCustomToast("Invalid amount or date!");
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
            } catch (Exception ignored) {}
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
}
